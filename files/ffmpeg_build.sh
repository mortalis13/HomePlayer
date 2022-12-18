BUILD_DIR=/root/_ffmpeg-android
NDK_TOOLCHAIN_DIR=/root/android_sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64
FFMPEG_DIR=/root/ffmpeg-5.1

ABIS=(
  armeabi-v7a
  arm64-v8a
  x86
  x86_64
)

ARCHS=(
  armv7a
  aarch64
  i686
  x86_64
)

rm -rf $BUILD_DIR/*

cd $FFMPEG_DIR

for i in $(seq 0 3); do
echo ==========================
echo ${ABIS[i]} -- ${ARCHS[i]}
echo ==========================
echo

ABI=${ABIS[i]}
ARCH=${ARCHS[i]}

ANDROID_VERSION=android21
if [ "$ABI" = "armeabi-v7a" ]; then
  ANDROID_VERSION=androideabi21
fi

ASM_FLAGS=
if [ "$ABI" = "x86" ]; then
  # ticket https://trac.ffmpeg.org/ticket/4928
  ASM_FLAGS=--disable-asm
fi


./configure \
\
--prefix=$BUILD_DIR/$ABI \
--target-os=android \
--arch=$ARCH \
--sysroot=$NDK_TOOLCHAIN_DIR/sysroot \
--enable-cross-compile \
\
--cc=$NDK_TOOLCHAIN_DIR/bin/$ARCH-linux-$ANDROID_VERSION-clang \
--cxx=$NDK_TOOLCHAIN_DIR/bin/$ARCH-linux-$ANDROID_VERSION-clang++ \
--ar=$NDK_TOOLCHAIN_DIR/bin/llvm-ar \
--nm=$NDK_TOOLCHAIN_DIR/bin/llvm-nm \
--ranlib=$NDK_TOOLCHAIN_DIR/bin/llvm-ranlib \
--strip=$NDK_TOOLCHAIN_DIR/bin/llvm-strip \
--x86asmexe=$NDK_TOOLCHAIN_DIR/bin/yasm \
$ASM_FLAGS \
\
--extra-cflags="-O3 -fPIC -w" \
--enable-shared \
--disable-static \
\
--disable-programs \
--disable-doc \
--disable-debug \
--disable-autodetect \
--disable-everything \
\
--disable-vulkan \
--disable-v4l2-m2m \
--disable-zlib \
\
--disable-avdevice \
--disable-swscale \
--disable-postproc \
--disable-avfilter \
--disable-network \
\
--enable-protocol=file \
\
--enable-demuxer=image2 \
--enable-demuxer=aac \
--enable-demuxer=ac3 \
--enable-demuxer=aiff \
--enable-demuxer=ape \
--enable-demuxer=asf \
--enable-demuxer=au \
--enable-demuxer=flac \
--enable-demuxer=m4v \
--enable-demuxer=mp3 \
--enable-demuxer=mpc \
--enable-demuxer=mpc8 \
--enable-demuxer=ogg \
--enable-demuxer=pcm_alaw \
--enable-demuxer=pcm_mulaw \
--enable-demuxer=pcm_f64be \
--enable-demuxer=pcm_f64le \
--enable-demuxer=pcm_f32be \
--enable-demuxer=pcm_f32le \
--enable-demuxer=pcm_s32be \
--enable-demuxer=pcm_s32le \
--enable-demuxer=pcm_s24be \
--enable-demuxer=pcm_s24le \
--enable-demuxer=pcm_s16be \
--enable-demuxer=pcm_s16le \
--enable-demuxer=pcm_s8 \
--enable-demuxer=pcm_u32be \
--enable-demuxer=pcm_u32le \
--enable-demuxer=pcm_u24be \
--enable-demuxer=pcm_u24le \
--enable-demuxer=pcm_u16be \
--enable-demuxer=pcm_u16le \
--enable-demuxer=pcm_u8 \
--enable-demuxer=rm \
--enable-demuxer=shorten \
--enable-demuxer=tak \
--enable-demuxer=tta \
--enable-demuxer=wav \
--enable-demuxer=wv \
--enable-demuxer=xwma \
--enable-demuxer=dsf \
\
--enable-decoder=aac \
--enable-decoder=aac_latm \
--enable-decoder=ac3 \
--enable-decoder=alac \
--enable-decoder=als \
--enable-decoder=ape \
--enable-decoder=atrac1 \
--enable-decoder=atrac3 \
--enable-decoder=eac3 \
--enable-decoder=flac \
--enable-decoder=gsm \
--enable-decoder=gsm_ms \
--enable-decoder=mp1 \
--enable-decoder=mp1float \
--enable-decoder=mp2 \
--enable-decoder=mp2float \
--enable-decoder=mp3 \
--enable-decoder=mp3adu \
--enable-decoder=mp3adufloat \
--enable-decoder=mp3float \
--enable-decoder=mp3on4 \
--enable-decoder=mp3on4float \
--enable-decoder=mpc7 \
--enable-decoder=mpc8 \
--enable-decoder=opus \
--enable-decoder=ra_144 \
--enable-decoder=ra_288 \
--enable-decoder=ralf \
--enable-decoder=shorten \
--enable-decoder=tak \
--enable-decoder=tta \
--enable-decoder=vorbis \
--enable-decoder=wavpack \
--enable-decoder=wmalossless \
--enable-decoder=wmapro \
--enable-decoder=wmav1 \
--enable-decoder=wmav2 \
--enable-decoder=wmavoice \
--enable-decoder=pcm_alaw \
--enable-decoder=pcm_bluray \
--enable-decoder=pcm_dvd \
--enable-decoder=pcm_f32be \
--enable-decoder=pcm_f32le \
--enable-decoder=pcm_f64be \
--enable-decoder=pcm_f64le \
--enable-decoder=pcm_lxf \
--enable-decoder=pcm_mulaw \
--enable-decoder=pcm_s8 \
--enable-decoder=pcm_s8_planar \
--enable-decoder=pcm_s16be \
--enable-decoder=pcm_s16be_planar \
--enable-decoder=pcm_s16le \
--enable-decoder=pcm_s16le_planar \
--enable-decoder=pcm_s24be \
--enable-decoder=pcm_s24daud \
--enable-decoder=pcm_s24le \
--enable-decoder=pcm_s24le_planar \
--enable-decoder=pcm_s32be \
--enable-decoder=pcm_s32le \
--enable-decoder=pcm_s32le_planar \
--enable-decoder=pcm_u8 \
--enable-decoder=pcm_u16be \
--enable-decoder=pcm_u16le \
--enable-decoder=pcm_u24be \
--enable-decoder=pcm_u24le \
--enable-decoder=pcm_u32be \
--enable-decoder=pcm_u32le \
--enable-decoder=dsd_lsbf \
--enable-decoder=dsd_msbf \
--enable-decoder=dsd_lsbf_planar \
--enable-decoder=dsd_msbf_planar \
|| exit 1

make clean
make -j$(nproc) || exit 1
make install

rm -rf $BUILD_DIR/$ABI/lib/pkgconfig/
rm -rf $BUILD_DIR/$ABI/share/

echo
echo

done
