#ifndef NATIVE_OPTICAL_FLOW_TRACKER_H
#define NATIVE_OPTICAL_FLOW_TRACKER_H

#include <jni.h>
#include <vector>
#include <cmath>
#include <cstdint>
#include <algorithm>

#ifdef __cplusplus
extern "C" {
#endif

// JNI Function Declarations matching com.example.engine.NativeOpticalFlowTracker

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
);

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
);

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
);

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
);

JNIEXPORT void JNICALL
Java_com_example_engine_NativeOpticalFlowTracker_nativeRelease(
    JNIEnv *env,
    jobject thiz,
    jlong nativeHandle
);

#ifdef __cplusplus
}
#endif

namespace cardtracker {

struct FeaturePoint {
    float x;
    float y;
    bool isValid;
};

struct FingerprintModel {
    std::vector<float> zeroMean;
    float norm = 1.0f;
    float meanBrightness = 200.0f;
    bool isRedSuit = false;
    bool isBlackSuit = false;
    bool isSet = false;
};

class NativeOpticalFlowCore {
public:
    NativeOpticalFlowCore(float initX, float initY, float initW, float initH, int fW, int fH);
    ~NativeOpticalFlowCore();

    void setFingerprint(const float* zeroMean, int length, float norm, float meanBrightness, bool isRed, bool isBlack);
    int processFrame(const uint8_t* grayPixels, int width, int height, int frameNumber, int64_t timestampMs, float* outResults);

private:
    float currentX;
    float currentY;
    float widthNorm;
    float heightNorm;
    int frameWidth;
    int frameHeight;

    float velocityX;
    float velocityY;
    int consecutiveOcclusions;
    int stationaryFrames;
    int trackingStateOrdinal; // 0=INIT, 1=TRACKING, 2=OCCLUDED, 3=REACQUIRED, 4=RECOVERED_FINGERPRINT, 5=LOST

    FingerprintModel fingerprint;
    std::vector<FeaturePoint> trackedPoints;
    std::vector<uint8_t> previousFrameGray;

    // Slot centers and ROI boundaries
    static constexpr float ROI_X_MIN = 0.380f;
    static constexpr float ROI_X_MAX = 0.771f;
    static constexpr float ROI_Y_CENTER = 0.530f;
    static constexpr float SLOT_LEFT = 0.430f;
    static constexpr float SLOT_MIDDLE = 0.575f;
    static constexpr float SLOT_RIGHT = 0.720f;
    static constexpr float BOUNDARY_1_2 = 0.5025f;
    static constexpr float BOUNDARY_2_3 = 0.6475f;

    void initializeFeatures();
    float computeZncc(const uint8_t* gray, int w, int h, float cx, float cy);
    int classifySlot(float x) const;
};

} // namespace cardtracker

#endif // NATIVE_OPTICAL_FLOW_TRACKER_H
