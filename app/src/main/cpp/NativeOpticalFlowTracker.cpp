#include "NativeOpticalFlowTracker.h"
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <vector>
#include <algorithm>

#define TAG "CardTrackerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ARM NEON SIMD Vectorized Dot Product
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>

static inline float neon_dot_product(const float* a, const float* b, int n) {
    float32x4_t sum4 = vdupq_n_f32(0.0f);
    int i = 0;
    for (; i <= n - 4; i += 4) {
        float32x4_t va = vld1q_f32(a + i);
        float32x4_t vb = vld1q_f32(b + i);
        sum4 = vmlaq_f32(sum4, va, vb);
    }
    float sum = vgetq_lane_f32(sum4, 0) + vgetq_lane_f32(sum4, 1) +
                vgetq_lane_f32(sum4, 2) + vgetq_lane_f32(sum4, 3);
    for (; i < n; ++i) {
        sum += a[i] * b[i];
    }
    return sum;
}
#else
static inline float neon_dot_product(const float* a, const float* b, int n) {
    float sum = 0.0f;
    for (int i = 0; i < n; ++i) {
        sum += a[i] * b[i];
    }
    return sum;
}
#endif

namespace cardtracker {

NativeOpticalFlowCore::NativeOpticalFlowCore(float initX, float initY, float initW, float initH, int fW, int fH)
    : currentX(initX), currentY(initY), widthNorm(initW), heightNorm(initH),
      frameWidth(fW), frameHeight(fH), velocityX(0.0f), velocityY(0.0f),
      consecutiveOcclusions(0), stationaryFrames(0), trackingStateOrdinal(1) {
    initializeFeatures();
}

NativeOpticalFlowCore::~NativeOpticalFlowCore() {
    trackedPoints.clear();
    previousFrameGray.clear();
}

void NativeOpticalFlowCore::setFingerprint(const float* zeroMean, int length, float norm, float meanBrightness, bool isRed, bool isBlack) {
    fingerprint.zeroMean.assign(zeroMean, zeroMean + length);
    fingerprint.norm = std::max(1e-4f, norm);
    fingerprint.meanBrightness = meanBrightness;
    fingerprint.isRedSuit = isRed;
    fingerprint.isBlackSuit = isBlack;
    fingerprint.isSet = true;
}

void NativeOpticalFlowCore::initializeFeatures() {
    trackedPoints.clear();
    float x1 = (currentX - widthNorm * 0.45f) * frameWidth;
    float x2 = (currentX + widthNorm * 0.45f) * frameWidth;
    float y1 = (currentY - heightNorm * 0.45f) * frameHeight;
    float y2 = (currentY + heightNorm * 0.45f) * frameHeight;

    int cols = 7;
    int rows = 9;
    for (int r = 0; r < rows; ++r) {
        float py = y1 + (y2 - y1) * (r + 0.5f) / rows;
        for (int c = 0; c < cols; ++c) {
            float px = x1 + (x2 - x1) * (c + 0.5f) / cols;
            trackedPoints.push_back({px, py, true});
        }
    }
}

int NativeOpticalFlowCore::classifySlot(float x) const {
    if (x < BOUNDARY_1_2) return 1; // Left
    if (x <= BOUNDARY_2_3) return 2; // Middle
    return 3; // Right
}

// Bilinear sample grayscale pixel
static inline float sampleBilinear(const uint8_t* gray, int w, int h, float x, float y) {
    if (x < 0.0f || x >= w - 1 || y < 0.0f || y >= h - 1) return 0.0f;
    int x0 = static_cast<int>(x);
    int y0 = static_cast<int>(y);
    float fx = x - x0;
    float fy = y - y0;
    float p00 = gray[y0 * w + x0];
    float p10 = gray[y0 * w + x0 + 1];
    float p01 = gray[(y0 + 1) * w + x0];
    float p11 = gray[(y0 + 1) * w + x0 + 1];
    return (1.0f - fx) * (1.0f - fy) * p00 +
           fx * (1.0f - fy) * p10 +
           (1.0f - fx) * fy * p01 +
           fx * fy * p11;
}

float NativeOpticalFlowCore::computeZncc(const uint8_t* gray, int w, int h, float cx, float cy) {
    if (!fingerprint.isSet || fingerprint.zeroMean.empty()) return 0.0f;

    const int tw = 32;
    const int th = 44;
    const int n = tw * th;
    if (static_cast<int>(fingerprint.zeroMean.size()) != n) return 0.0f;

    float patch[tw * th];
    float sum = 0.0f;

    float x1 = (cx - widthNorm * 0.45f) * w;
    float x2 = (cx + widthNorm * 0.45f) * w;
    float y1 = (cy - heightNorm * 0.45f) * h;
    float y2 = (cy + heightNorm * 0.45f) * h;

    int idx = 0;
    for (int y = 0; y < th; ++y) {
        float sy = y1 + (y2 - y1) * (y + 0.5f) / th;
        for (int x = 0; x < tw; ++x) {
            float sx = x1 + (x2 - x1) * (x + 0.5f) / tw;
            float val = sampleBilinear(gray, w, h, sx, sy);
            patch[idx++] = val;
            sum += val;
        }
    }

    float mean = sum / n;
    float normSq = 0.0f;
    for (int i = 0; i < n; ++i) {
        patch[i] -= mean;
        normSq += patch[i] * patch[i];
    }
    float norm = std::sqrt(normSq);
    if (norm < 1e-4f) return 0.0f;

    // Use ARM NEON SIMD accelerated dot product
    float dot = neon_dot_product(patch, fingerprint.zeroMean.data(), n);
    float zncc = dot / (norm * fingerprint.norm);
    return std::max(-1.0f, std::min(1.0f, zncc));
}

int NativeOpticalFlowCore::processFrame(
    const uint8_t* grayPixels,
    int width,
    int height,
    int frameNumber,
    int64_t timestampMs,
    float* outResults
) {
    if (previousFrameGray.empty()) {
        previousFrameGray.assign(grayPixels, grayPixels + (width * height));
        outResults[0] = currentX;
        outResults[1] = currentY;
        outResults[2] = 1.0f; // Confidence
        outResults[3] = static_cast<float>(trackedPoints.size()); // Inliers
        outResults[4] = 0.0f; // VelX
        outResults[5] = 0.0f; // VelY
        outResults[6] = 1.0f; // TRACKING
        outResults[7] = static_cast<float>(classifySlot(currentX));
        return 0;
    }

    // Pyramidal Lucas-Kanade Vectorized Tracking Step
    std::vector<float> validDx;
    std::vector<float> validDy;
    const int winSize = 7;
    const uint8_t* prev = previousFrameGray.data();
    const uint8_t* curr = grayPixels;

    for (auto& pt : trackedPoints) {
        if (!pt.isValid) continue;

        float px = pt.x;
        float py = pt.y;
        float gxx = 0.0f, gyy = 0.0f, gxy = 0.0f;
        float bx = 0.0f, by = 0.0f;

        // Compute spatial gradients and temporal difference in search window
        for (int wy = -winSize; wy <= winSize; ++wy) {
            for (int wx = -winSize; wx <= winSize; ++wx) {
                float x = px + wx;
                float y = py + wy;
                if (x < 1 || x >= width - 2 || y < 1 || y >= height - 2) continue;

                float p = sampleBilinear(prev, width, height, x, y);
                float c = sampleBilinear(curr, width, height, x, y);
                float it = c - p;

                float ix = (sampleBilinear(prev, width, height, x + 1, y) - sampleBilinear(prev, width, height, x - 1, y)) * 0.5f;
                float iy = (sampleBilinear(prev, width, height, x, y + 1) - sampleBilinear(prev, width, height, x, y - 1)) * 0.5f;

                gxx += ix * ix;
                gyy += iy * iy;
                gxy += ix * iy;
                bx += it * ix;
                by += it * iy;
            }
        }

        float det = gxx * gyy - gxy * gxy;
        if (det > 1e-3f) {
            float dx = -(gyy * bx - gxy * by) / det;
            float dy = -(gxx * by - gxy * bx) / det;

            if (std::abs(dx) < 25.0f && std::abs(dy) < 15.0f) {
                validDx.push_back(dx);
                validDy.push_back(dy);
                pt.x += dx;
                pt.y += dy;
            } else {
                pt.isValid = false;
            }
        } else {
            pt.isValid = false;
        }
    }

    // Kinematic motion consensus
    float shiftX = 0.0f;
    float shiftY = 0.0f;
    bool flowReliable = false;

    if (validDx.size() >= 10) {
        std::sort(validDx.begin(), validDx.end());
        std::sort(validDy.begin(), validDy.end());
        shiftX = validDx[validDx.size() / 2] / width;
        shiftY = validDy[validDy.size() / 2] / height;
        flowReliable = true;
    }

    float predictedX = currentX + (flowReliable ? shiftX : velocityX * 0.8f);
    predictedX = std::max(ROI_X_MIN, std::min(ROI_X_MAX, predictedX));

    // Multi-slot ZNCC candidate evaluation (Left, Middle, Right)
    float znccLeft = computeZncc(grayPixels, width, height, SLOT_LEFT, ROI_Y_CENTER);
    float znccMid = computeZncc(grayPixels, width, height, SLOT_MIDDLE, ROI_Y_CENTER);
    float znccRight = computeZncc(grayPixels, width, height, SLOT_RIGHT, ROI_Y_CENTER);
    float znccPred = computeZncc(grayPixels, width, height, predictedX, ROI_Y_CENTER);

    float maxSlotZncc = std::max({znccLeft, znccMid, znccRight});
    float bestSlotX = (maxSlotZncc == znccLeft) ? SLOT_LEFT : ((maxSlotZncc == znccMid) ? SLOT_MIDDLE : SLOT_RIGHT);

    // Target tracking state update & candidate recovery
    float targetX = predictedX;
    float confidence = 0.85f;
    trackingStateOrdinal = 1; // TRACKING

    if (!flowReliable || maxSlotZncc > znccPred + 0.15f) {
        if (maxSlotZncc > 0.45f) {
            targetX = bestSlotX;
            confidence = maxSlotZncc;
            trackingStateOrdinal = 4; // RECOVERED_FINGERPRINT
        } else {
            consecutiveOcclusions++;
            confidence = 0.35f;
            trackingStateOrdinal = 2; // OCCLUDED
        }
    } else {
        consecutiveOcclusions = 0;
        confidence = std::max(0.60f, znccPred);
    }

    if (confidence < 0.85f || consecutiveOcclusions > 4) {
        trackingStateOrdinal = 6; // UNRELIABLE
    }

    // Smooth trajectory update
    float alpha = 0.30f;
    velocityX = (targetX - currentX);
    currentX = currentX + alpha * velocityX;
    currentX = std::max(ROI_X_MIN, std::min(ROI_X_MAX, currentX));

    // Re-seed features around current center
    if (validDx.size() < 15 || trackingStateOrdinal == 4) {
        initializeFeatures();
    }

    previousFrameGray.assign(grayPixels, grayPixels + (width * height));

    outResults[0] = currentX;
    outResults[1] = ROI_Y_CENTER;
    outResults[2] = confidence;
    outResults[3] = static_cast<float>(validDx.size());
    outResults[4] = velocityX;
    outResults[5] = velocityY;
    outResults[6] = static_cast<float>(trackingStateOrdinal);
    outResults[7] = static_cast<float>(classifySlot(currentX));

    return 0;
}

} // namespace cardtracker

// JNI Bridge Implementations

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_engine_NativeOpticalFlowTracker_nativeInit(
    JNIEnv *env,
    jobject thiz,
    jfloat initialX,
    jfloat initialY,
    jfloat initialWidth,
    jfloat initialHeight,
    jint frameWidth,
    jint frameHeight
) {
    auto* core = new cardtracker::NativeOpticalFlowCore(
        initialX, initialY, initialWidth, initialHeight, frameWidth, frameHeight
    );
    return reinterpret_cast<jlong>(core);
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_NativeOpticalFlowTracker_nativeSetFingerprint(
    JNIEnv *env,
    jobject thiz,
    jlong nativeHandle,
    jfloatArray zeroMeanArray,
    jfloat norm,
    jfloat meanBrightness,
    jboolean isRedSuit,
    jboolean isBlackSuit
) {
    if (nativeHandle == 0 || zeroMeanArray == nullptr) return JNI_FALSE;
    auto* core = reinterpret_cast<cardtracker::NativeOpticalFlowCore*>(nativeHandle);

    jsize len = env->GetArrayLength(zeroMeanArray);
    jfloat* elems = env->GetFloatArrayElements(zeroMeanArray, nullptr);
    if (!elems) return JNI_FALSE;

    core->setFingerprint(elems, len, norm, meanBrightness, isRedSuit == JNI_TRUE, isBlackSuit == JNI_TRUE);

    env->ReleaseFloatArrayElements(zeroMeanArray, elems, JNI_ABORT);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_NativeOpticalFlowTracker_nativeProcessFrame(
    JNIEnv *env,
    jobject thiz,
    jlong nativeHandle,
    jlong matAddrGrayscale,
    jbyteArray rawGrayBuffer,
    jint width,
    jint height,
    jint frameNumber,
    jlong timestampMs,
    jfloatArray outResults
) {
    if (nativeHandle == 0 || outResults == nullptr) return -1;
    auto* core = reinterpret_cast<cardtracker::NativeOpticalFlowCore*>(nativeHandle);

    const uint8_t* grayPixels = nullptr;
    jboolean isCopy = JNI_FALSE;

    if (matAddrGrayscale != 0) {
        grayPixels = reinterpret_cast<const uint8_t*>(matAddrGrayscale);
    } else if (rawGrayBuffer != nullptr) {
        grayPixels = reinterpret_cast<const uint8_t*>(
            env->GetPrimitiveArrayCritical(rawGrayBuffer, &isCopy)
        );
    }

    if (!grayPixels) return -2;

    float results[8] = {0};
    int ret = core->processFrame(grayPixels, width, height, frameNumber, timestampMs, results);

    if (rawGrayBuffer != nullptr && matAddrGrayscale == 0) {
        env->ReleasePrimitiveArrayCritical(rawGrayBuffer, const_cast<uint8_t*>(grayPixels), JNI_ABORT);
    }

    env->SetFloatArrayRegion(outResults, 0, 8, results);
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_NativeOpticalFlowTracker_nativeProcessFrameDirect(
    JNIEnv *env,
    jobject thiz,
    jlong nativeHandle,
    jobject directBuffer,
    jint width,
    jint height,
    jint frameNumber,
    jlong timestampMs,
    jfloatArray outResults
) {
    if (nativeHandle == 0 || outResults == nullptr || directBuffer == nullptr) return -1;
    auto* core = reinterpret_cast<cardtracker::NativeOpticalFlowCore*>(nativeHandle);

    const uint8_t* grayPixels = reinterpret_cast<const uint8_t*>(
        env->GetDirectBufferAddress(directBuffer)
    );
    if (!grayPixels) return -2;

    float results[8] = {0};
    int ret = core->processFrame(grayPixels, width, height, frameNumber, timestampMs, results);
    env->SetFloatArrayRegion(outResults, 0, 8, results);
    return ret;
}

JNIEXPORT void JNICALL
Java_com_example_engine_NativeOpticalFlowTracker_nativeRelease(
    JNIEnv *env,
    jobject thiz,
    jlong nativeHandle
) {
    if (nativeHandle != 0) {
        auto* core = reinterpret_cast<cardtracker::NativeOpticalFlowCore*>(nativeHandle);
        delete core;
    }
}

} // extern "C"
