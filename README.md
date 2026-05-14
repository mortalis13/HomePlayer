# Home Player

Android audio player with flat design and direct directory navigation.

## Features
- Direct file system navigation (the main UI part is a file manager filtered by playable filetypes)
- Plain UI based on a single view (no additional activities, the controls and settings are displayed in panels on demand)
- Waveform slider
- The playback position is always saved
- Adjustable playback limit to up to 5 minutes (for previewing of multiple tracks in the same folder)
- Precise loop of a track section
- Custom 5-band EQ
- Volume control from the status bar
- CUE playlists support
- Headset controls support

## Usage Notes
- Pressing on the central button in the stats bar reveals the current track information
- Sliding left/right shows the adjacent tracks data
- Sliding down loads the album art and lyrics, if present in the audio file
- Sliding right in the main file navigator region changes path to the parent folder
- The same action is done when pressing the header with the current folder name
- Long-pressing the next track button switches to a random track from the currently played folder
- To repeat only selected tracks, enable the repeat button in the tracks context menu
- To set the loop points to the current playback position, press the corresponding time labels in the loop panel

## Build
- JDK 17, Gradle 8.4
- `gradle appStart` - build, install and launch on a device

## Technical Notes
- Uses the **Oboe** audio library as the audio engine
  - Defined in the `jni/FilePlayer` class
  - With standard audio configuration of 44100 Hz sample rate, 2 channels, floating point sample format
  - Initialized with `oboe::AudioStreamBuilder::openStream` and `oboe::AudioStream::requestStart`
  - The audio packets are processed with **ffmpeg** library, inside `jni/AudioDecoder`, and sent to the engine via `oboe::AudioStream::write`
- Waveform is built using the stream through processing with **ffmpeg** in `AudioDecoder::compressSamples`
- EQ is implemented via audio packets filtering through a 2nd order parametric EQ biquad filter with non-constant Q (a peaking filter) in `jni/AudioFilter` (see Chapter 11 of Designing Audio Effect Plugins in C++, 2nd edition by Will Pirkle)
