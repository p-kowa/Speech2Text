# Performance Testing Guide

## How to Test Whisper Performance After Optimization

### 1. Install the Optimized Build

```bash
# Build and install the release APK
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 2. Enable Developer Options and ADB Debugging

On your Samsung A33:
1. Go to **Settings → About Phone**
2. Tap **Build Number** 7 times to enable Developer Options
3. Go to **Settings → Developer Options**
4. Enable **USB Debugging**

### 3. Test Whisper Performance

#### 3.1 Basic Test
1. Open Speech2Text app
2. Select **"Whisper"** from recognition method dropdown
3. Choose language (e.g., German)
4. Press microphone button
5. Say: **"Das ist ein Test"** (speak clearly for 2-3 seconds)
6. Stop recording
7. **Measure time** from stop until transcription appears

**Expected Result:**
- **Before optimization:** ~60+ seconds
- **After optimization:** ~8-12 seconds
- **Speedup:** ~5-8x faster

#### 3.2 Monitor CPU Usage During Transcription

Open terminal and run:
```bash
# Monitor CPU usage in real-time
adb shell top -m 20 | grep speech2text
```

**Expected Result:**
- Multiple threads active (6-7 threads)
- CPU usage: 70-90% total across all cores
- Process name: `com.example.speech2text`

#### 3.3 Check Thread Count

```bash
# View logcat for thread count
adb logcat | grep "CPU cores:"
```

**Expected Output:**
```
WhisperNative: CPU cores: 8, using 7 threads
```

#### 3.4 Check Whisper Logs

```bash
# Full Whisper transcription logs
adb logcat | grep WhisperNative
```

**Look for:**
```
WhisperNative: Initializing Whisper with model: ...
WhisperNative: Loading Whisper model with flash_attn=false...
WhisperNative: Whisper context initialized successfully
WhisperNative: CPU cores: 8, using 7 threads
WhisperNative: Starting transcription...
WhisperNative: === Final transcription: [your text] ===
```

### 4. Detailed Performance Benchmarking

#### 4.1 Test Different Audio Lengths

| Test Audio | Expected Time (Tiny Q8) | Expected Time (Base Q8) |
|------------|-------------------------|-------------------------|
| "Hallo"    | 6-8 sec                | 12-16 sec              |
| "Das ist ein Test" | 8-12 sec    | 16-24 sec              |
| 10 seconds of speech | 15-25 sec  | 35-50 sec              |
| 30 seconds of speech | 60-90 sec  | 120+ sec               |

#### 4.2 CPU Core Frequency Monitoring

```bash
# Check CPU frequencies during transcription
adb shell "cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq"
```

**Expected Result (during transcription):**
- CPU 0-5 (A55): ~1800-2000 MHz
- CPU 6-7 (A78): ~2000-2400 MHz (performance cores boosting)

#### 4.3 Temperature Monitoring

```bash
# Monitor thermal state
adb shell dumpsys thermalservice
```

**Note:** If CPU throttling occurs (phone gets hot), performance will decrease.

### 5. Performance Comparison Table

Fill this in after testing:

| Metric | Before Optimization | After Optimization | Improvement |
|--------|-------------------|-------------------|-------------|
| Thread count | 1 | 6-7 | 6-7x |
| Transcription time | ~60 sec | ? sec | ?x faster |
| CPU usage | ~12% | ~75-90% | 6-7x |
| CPU architecture | armv8-a | armv8.2-a+dotprod | ✓ |
| Compiler flags | -O2 | -O3 -ffast-math | ✓ |

### 6. Verify NEON SIMD Optimizations

```bash
# Check compiled binary for NEON instructions
adb shell "cat /proc/cpuinfo | grep Features"
```

**Expected Features:**
```
Features : fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm jscvt fcma lrcpc dcpop sha3 sm3 sm4 asimddp sha512 sve asimdfhm dit uscat ilrcpc flagm ssbs sb paca pacg dcpodp sve2 sveaes svepmull svebitperm svesha3 svesm4 flagm2 frint
```

**Key features for performance:**
- `asimd` - ARM NEON SIMD
- `asimddp` - Dot product acceleration (ARMv8.2+)
- `fp` - Floating point
- `crc32` - Hardware CRC

### 7. Known Performance Issues

#### Issue 1: Thermal Throttling
**Symptom:** First transcription is fast, subsequent ones are slower
**Cause:** CPU temperature increases, causing frequency reduction
**Solution:** Wait for phone to cool between tests

#### Issue 2: Background Apps
**Symptom:** Inconsistent performance
**Cause:** Other apps using CPU resources
**Solution:** Close all background apps before testing

#### Issue 3: Battery Saver Mode
**Symptom:** Extremely slow transcription
**Cause:** CPU frequency capped by system
**Solution:** Disable battery saver, connect to charger

### 8. Advanced: CPU Affinity Testing

Create a test to pin threads to performance cores only:

```cpp
// In native-lib.cpp (experimental)
#include <sched.h>

cpu_set_t cpuset;
CPU_ZERO(&cpuset);
CPU_SET(6, &cpuset);  // A78 core 1
CPU_SET(7, &cpuset);  // A78 core 2
sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
```

**Expected:** Slightly better performance (15-20% faster) but phone gets hotter.

### 9. Model Comparison Test

Test all available models:

1. **ggml-tiny-q5_1.bin** (~31 MB)
   - Expected: Fastest (6-9 sec for short audio)
   - Quality: Good

2. **ggml-tiny-q8_0.bin** (~39 MB) ← Current
   - Expected: 8-12 sec for short audio
   - Quality: Very Good

3. **ggml-tiny.bin** (~75 MB, F16)
   - Expected: 12-18 sec for short audio
   - Quality: Excellent

4. **ggml-base-q8_0.bin** (~78 MB)
   - Expected: 16-24 sec for short audio
   - Quality: Best

### 10. Report Results

After testing, update `PERFORMANCE_ANALYSIS.md` with:
- Actual transcription times
- CPU usage screenshots
- Temperature observations
- Any unexpected behavior

### Summary Checklist

- [ ] Build completed successfully
- [ ] App installed on Samsung A33
- [ ] Short audio test (2-3 sec) completed
- [ ] Thread count verified (6-7 threads)
- [ ] CPU usage checked (70-90%)
- [ ] Transcription time measured
- [ ] Performance improvement calculated
- [ ] No crashes or errors
- [ ] Temperature is acceptable
- [ ] Results documented

---

**Target Performance (Samsung A33 with Exynos 1280):**
- **Short audio (2-3 sec):** 8-12 seconds transcription time
- **Real-time factor:** 4-6x (audio processes 4-6x slower than real-time)
- **CPU usage:** 75-90% across 6-7 cores
- **Thread count:** 6-7 threads active

If performance doesn't meet these targets, check:
1. Build is Release variant (not Debug)
2. No background apps running
3. Battery saver mode disabled
4. Phone is not overheating
5. All compiler optimizations applied correctly

