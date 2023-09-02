# ===================
# == Installation
# ..Get NDK download url
# curl -s https://developer.android.com/ndk/downloads/index.html | grep -Eo 'https://.+linux.zip'

# ..Download and unpack
# wget https://dl.google.com/android/repository/android-ndk-r25b-linux.zip
# wget http://ffmpeg.org/releases/ffmpeg-6.0.tar.gz
# apt install unzip
# tar -xzvf ffmpeg-6.0.tar.gz

# ..Or get the git repository
# git clone https://github.com/FFmpeg/FFmpeg ffmpeg
# git checkout n6.0

# ..Run the script
# ===================

FULL_BUILD=0

BUILD_DIR=/root/_ffmpeg-android
FFMPEG_DIR=/root/ffmpeg

NDK_TOOLCHAIN_DIR=/root/android-ndk-r25b/toolchains/llvm/prebuilt/linux-x86_64
NDK_MAKE_DIR=/root/android-ndk-r25b/prebuilt/linux-x86_64/bin

PATH=$PATH:$NDK_MAKE_DIR

ABIS=(
  arm64-v8a
  armeabi-v7a
  x86
  x86_64
)

ARCHS=(
  aarch64
  armv7a
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

CONFIG=(
--prefix=$BUILD_DIR/$ABI
--target-os=android
--arch=$ARCH
--sysroot=$NDK_TOOLCHAIN_DIR/sysroot
--enable-cross-compile

--cc=$NDK_TOOLCHAIN_DIR/bin/$ARCH-linux-$ANDROID_VERSION-clang
--cxx=$NDK_TOOLCHAIN_DIR/bin/$ARCH-linux-$ANDROID_VERSION-clang++
--ar=$NDK_TOOLCHAIN_DIR/bin/llvm-ar
--nm=$NDK_TOOLCHAIN_DIR/bin/llvm-nm
--ranlib=$NDK_TOOLCHAIN_DIR/bin/llvm-ranlib
--strip=$NDK_TOOLCHAIN_DIR/bin/llvm-strip
--x86asmexe=$NDK_TOOLCHAIN_DIR/bin/yasm
$ASM_FLAGS

--extra-cflags="-O3 -fPIC -w"
--enable-shared
--disable-static
--disable-doc
--disable-debug
--disable-vulkan
--disable-v4l2-m2m
--disable-zlib
)

ADD_CONFIG=
if [ $FULL_BUILD -eq 0 ]; then
ADD_CONFIG=(
--disable-programs
--disable-autodetect
--disable-everything

--disable-avdevice
--disable-swscale
--disable-postproc
--disable-avfilter
--disable-network

--enable-protocol=file

--enable-parser=aac
--enable-parser=aac_latm
--enable-parser=ac3
--enable-parser=adx
--enable-parser=amr
--enable-parser=cook
--enable-parser=dca
--enable-parser=dvaudio
--enable-parser=flac
--enable-parser=g723_1
--enable-parser=g729
--enable-parser=gsm
--enable-parser=mlp
--enable-parser=mpegaudio
--enable-parser=opus
--enable-parser=sbc
--enable-parser=sipr
--enable-parser=tak
--enable-parser=vorbis
--enable-parser=xma

--enable-demuxer=aa
--enable-demuxer=aac
--enable-demuxer=aax
--enable-demuxer=ac3
--enable-demuxer=ace
--enable-demuxer=acm
--enable-demuxer=act
--enable-demuxer=adf
--enable-demuxer=adp
--enable-demuxer=ads
--enable-demuxer=adx
--enable-demuxer=aea
--enable-demuxer=afc
--enable-demuxer=aiff
--enable-demuxer=aix
--enable-demuxer=alp
--enable-demuxer=amr
--enable-demuxer=amrnb
--enable-demuxer=amrwb
--enable-demuxer=anm
--enable-demuxer=apc
--enable-demuxer=ape
--enable-demuxer=apm
--enable-demuxer=aptx
--enable-demuxer=aptx_hd
--enable-demuxer=asf
--enable-demuxer=au
--enable-demuxer=avi
--enable-demuxer=bink
--enable-demuxer=binka
--enable-demuxer=boa
--enable-demuxer=caf
--enable-demuxer=daud
--enable-demuxer=dfpwm
--enable-demuxer=dsf
--enable-demuxer=ea
--enable-demuxer=eac3
--enable-demuxer=epaf
--enable-demuxer=flac
--enable-demuxer=g722
--enable-demuxer=g723_1
--enable-demuxer=g726
--enable-demuxer=g726le
--enable-demuxer=g729
--enable-demuxer=gsm
--enable-demuxer=hca
--enable-demuxer=hcom
--enable-demuxer=ilbc
--enable-demuxer=matroska
--enable-demuxer=m4v
--enable-demuxer=mca
--enable-demuxer=mcc
--enable-demuxer=mlp
--enable-demuxer=mov
--enable-demuxer=mp3
--enable-demuxer=mpc
--enable-demuxer=mpc8
--enable-demuxer=mtaf
--enable-demuxer=ogg
--enable-demuxer=oma
--enable-demuxer=pcm_alaw
--enable-demuxer=pcm_f32be
--enable-demuxer=pcm_f32le
--enable-demuxer=pcm_f64be
--enable-demuxer=pcm_f64le
--enable-demuxer=pcm_mulaw
--enable-demuxer=pcm_s16be
--enable-demuxer=pcm_s16le
--enable-demuxer=pcm_s24be
--enable-demuxer=pcm_s24le
--enable-demuxer=pcm_s32be
--enable-demuxer=pcm_s32le
--enable-demuxer=pcm_s8
--enable-demuxer=pcm_u16be
--enable-demuxer=pcm_u16le
--enable-demuxer=pcm_u24be
--enable-demuxer=pcm_u24le
--enable-demuxer=pcm_u32be
--enable-demuxer=pcm_u32le
--enable-demuxer=pcm_u8
--enable-demuxer=pcm_vidc
--enable-demuxer=rm
--enable-demuxer=sbc
--enable-demuxer=shorten
--enable-demuxer=smacker
--enable-demuxer=swf
--enable-demuxer=tak
--enable-demuxer=thp
--enable-demuxer=truehd
--enable-demuxer=tta
--enable-demuxer=wav
--enable-demuxer=wsaud
--enable-demuxer=wv
--enable-demuxer=wve
--enable-demuxer=xa
--enable-demuxer=xwma

--enable-decoder=aac
--enable-decoder=aac_fixed
--enable-decoder=aac_latm
--enable-decoder=ac3
--enable-decoder=ac3_fixed
--enable-decoder=acelp_kelvin
--enable-decoder=adpcm_4xm
--enable-decoder=adpcm_adx
--enable-decoder=adpcm_afc
--enable-decoder=adpcm_agm
--enable-decoder=adpcm_aica
--enable-decoder=adpcm_argo
--enable-decoder=adpcm_ct
--enable-decoder=adpcm_dtk
--enable-decoder=adpcm_ea
--enable-decoder=adpcm_ea_maxis_xa
--enable-decoder=adpcm_ea_r1
--enable-decoder=adpcm_ea_r2
--enable-decoder=adpcm_ea_r3
--enable-decoder=adpcm_ea_xas
--enable-decoder=adpcm_g722
--enable-decoder=adpcm_g726
--enable-decoder=adpcm_g726le
--enable-decoder=adpcm_ima_acorn
--enable-decoder=adpcm_ima_alp
--enable-decoder=adpcm_ima_amv
--enable-decoder=adpcm_ima_apc
--enable-decoder=adpcm_ima_apm
--enable-decoder=adpcm_ima_cunning
--enable-decoder=adpcm_ima_dat4
--enable-decoder=adpcm_ima_dk3
--enable-decoder=adpcm_ima_dk4
--enable-decoder=adpcm_ima_ea_eacs
--enable-decoder=adpcm_ima_ea_sead
--enable-decoder=adpcm_ima_iss
--enable-decoder=adpcm_ima_moflex
--enable-decoder=adpcm_ima_mtf
--enable-decoder=adpcm_ima_oki
--enable-decoder=adpcm_ima_qt
--enable-decoder=adpcm_ima_rad
--enable-decoder=adpcm_ima_smjpeg
--enable-decoder=adpcm_ima_ssi
--enable-decoder=adpcm_ima_wav
--enable-decoder=adpcm_ima_ws
--enable-decoder=adpcm_ms
--enable-decoder=adpcm_mtaf
--enable-decoder=adpcm_psx
--enable-decoder=adpcm_sbpro_2
--enable-decoder=adpcm_sbpro_3
--enable-decoder=adpcm_sbpro_4
--enable-decoder=adpcm_swf
--enable-decoder=adpcm_thp
--enable-decoder=adpcm_thp_le
--enable-decoder=adpcm_vima
--enable-decoder=adpcm_xa
--enable-decoder=adpcm_yamaha
--enable-decoder=adpcm_zork
--enable-decoder=alac
--enable-decoder=als
--enable-decoder=amrnb
--enable-decoder=amrwb
--enable-decoder=ape
--enable-decoder=aptx
--enable-decoder=aptx_hd
--enable-decoder=atrac1
--enable-decoder=atrac3
--enable-decoder=atrac3al
--enable-decoder=atrac3p
--enable-decoder=atrac3p
--enable-decoder=atrac3pal
--enable-decoder=atrac3pal
--enable-decoder=atrac9
--enable-decoder=bink
--enable-decoder=binkaudio_dct
--enable-decoder=binkaudio_rdft
--enable-decoder=bmv_audio
--enable-decoder=comfortnoise
--enable-decoder=cook
--enable-decoder=dca
--enable-decoder=dds
--enable-decoder=derf_dpcm
--enable-decoder=dfpwm
--enable-decoder=dolby_e
--enable-decoder=dsd_lsbf
--enable-decoder=dsd_lsbf_planar
--enable-decoder=dsd_msbf
--enable-decoder=dsd_msbf_planar
--enable-decoder=dsicinaudio
--enable-decoder=dss_sp
--enable-decoder=dst
--enable-decoder=dvaudio
--enable-decoder=eac3
--enable-decoder=evrc
--enable-decoder=fastaudio
--enable-decoder=ffwavesynth
--enable-decoder=flac
--enable-decoder=g723_1
--enable-decoder=g729
--enable-decoder=gremlin_dpcm
--enable-decoder=gsm
--enable-decoder=gsm_ms
--enable-decoder=hca
--enable-decoder=hcom
--enable-decoder=iac
--enable-decoder=ilbc
--enable-decoder=imc
--enable-decoder=interplay_acm
--enable-decoder=interplay_acm
--enable-decoder=interplay_dpcm
--enable-decoder=mace3
--enable-decoder=mace6
--enable-decoder=metasound
--enable-decoder=mlp
--enable-decoder=mp1
--enable-decoder=mp1float
--enable-decoder=mp2
--enable-decoder=mp2float
--enable-decoder=mp3
--enable-decoder=mp3adu
--enable-decoder=mp3adufloat
--enable-decoder=mp3float
--enable-decoder=mp3on4
--enable-decoder=mp3on4float
--enable-decoder=mpc7
--enable-decoder=mpc8
--enable-decoder=msnsiren
--enable-decoder=nellymoser
--enable-decoder=on2avc
--enable-decoder=opus
--enable-decoder=paf_audio
--enable-decoder=pcm_alaw
--enable-decoder=pcm_bluray
--enable-decoder=pcm_dvd
--enable-decoder=pcm_f16le
--enable-decoder=pcm_f24le
--enable-decoder=pcm_f32be
--enable-decoder=pcm_f32le
--enable-decoder=pcm_f64be
--enable-decoder=pcm_f64le
--enable-decoder=pcm_lxf
--enable-decoder=pcm_mulaw
--enable-decoder=pcm_s16be
--enable-decoder=pcm_s16be_planar
--enable-decoder=pcm_s16le
--enable-decoder=pcm_s16le_planar
--enable-decoder=pcm_s24be
--enable-decoder=pcm_s24daud
--enable-decoder=pcm_s24le
--enable-decoder=pcm_s24le_planar
--enable-decoder=pcm_s32be
--enable-decoder=pcm_s32le
--enable-decoder=pcm_s32le_planar
--enable-decoder=pcm_s64be
--enable-decoder=pcm_s64le
--enable-decoder=pcm_s8
--enable-decoder=pcm_s8_planar
--enable-decoder=pcm_sga
--enable-decoder=pcm_u16be
--enable-decoder=pcm_u16le
--enable-decoder=pcm_u24be
--enable-decoder=pcm_u24le
--enable-decoder=pcm_u32be
--enable-decoder=pcm_u32le
--enable-decoder=pcm_u8
--enable-decoder=pcm_vidc
--enable-decoder=qcelp
--enable-decoder=qdm2
--enable-decoder=qdmc
--enable-decoder=ra_144
--enable-decoder=ra_288
--enable-decoder=ralf
--enable-decoder=roq_dpcm
--enable-decoder=s302m
--enable-decoder=sbc
--enable-decoder=sdx2_dpcm
--enable-decoder=shorten
--enable-decoder=sipr
--enable-decoder=siren
--enable-decoder=smackaud
--enable-decoder=smacker
--enable-decoder=sol_dpcm
--enable-decoder=sonic
--enable-decoder=speex
--enable-decoder=tak
--enable-decoder=truehd
--enable-decoder=truespeech
--enable-decoder=tta
--enable-decoder=twinvq
--enable-decoder=vmdaudio
--enable-decoder=vorbis
--enable-decoder=wavpack
--enable-decoder=wmalossless
--enable-decoder=wmapro
--enable-decoder=wmav1
--enable-decoder=wmav2
--enable-decoder=wmavoice
--enable-decoder=ws_snd1
--enable-decoder=xan_dpcm
--enable-decoder=xma1
--enable-decoder=xma2
)
else
  echo ">> Full build"
fi

./configure "${CONFIG[@]}" "${ADD_CONFIG[@]}" || exit 1

make clean
make -j$(nproc) || exit 1
make install

rm -rf $BUILD_DIR/$ABI/bin
rm -rf $BUILD_DIR/$ABI/lib/pkgconfig
rm -rf $BUILD_DIR/$ABI/share

echo
echo

done
