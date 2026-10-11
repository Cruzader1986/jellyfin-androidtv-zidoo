package org.jellyfin.androidtv.util.profile

import org.jellyfin.androidtv.constant.Codec
import org.jellyfin.androidtv.util.profile.ProfileHelper.subtitleProfile
import org.jellyfin.apiclient.model.dlna.CodecProfile
import org.jellyfin.apiclient.model.dlna.CodecType
import org.jellyfin.apiclient.model.dlna.DeviceProfile
import org.jellyfin.apiclient.model.dlna.DirectPlayProfile
import org.jellyfin.apiclient.model.dlna.DlnaProfileType
import org.jellyfin.apiclient.model.dlna.EncodingContext
import org.jellyfin.apiclient.model.dlna.SubtitleDeliveryMethod
import org.jellyfin.apiclient.model.dlna.TranscodingProfile

@Suppress("MagicNumber")
class ZidooPlayerProfile(
	isDTSEnabled: Boolean = false,
	forceCompliantSurroundCodecs: Boolean = false,
	forcedAudioCodec: String? = null,
	forceNumChannels: Int? = null
) : DeviceProfile() {

	private val codecsDolby = arrayOf(
		Codec.Audio.AC3,
		Codec.Audio.EAC3,
		Codec.Audio.TRUEHD,
	)
	private val codecsDTS = arrayOf(
		Codec.Audio.DTS,
		Codec.Audio.DCA,
	)
	private val codecsCommon = arrayOf(
		Codec.Audio.AAC,
		Codec.Audio.AAC_LATM,
		Codec.Audio.ALAC,
		Codec.Audio.FLAC,
		Codec.Audio.MP2,
		Codec.Audio.MP3,
		Codec.Audio.OGG,
		Codec.Audio.OPUS,
		Codec.Audio.VORBIS,
	)
	private val codecsPcm = arrayOf(
		Codec.Audio.PCM,
		Codec.Audio.PCM_ALAW,
		Codec.Audio.PCM_MULAW,
		Codec.Audio.PCM_S16LE,
		Codec.Audio.PCM_S24LE,
	)
	private val codecsRare = arrayOf(
		Codec.Audio.WEBMA,
		Codec.Audio.MPA,
		Codec.Audio.WAV,
		Codec.Audio.WMA,
		Codec.Audio.WMAV2,
//		Codec.Audio.DSD,
		Codec.Audio.MLP,
	)

	private val codecsVideo = arrayOf(
		Codec.Video.H264,
		Codec.Video.HEVC,
		Codec.Video.MPEG,
		Codec.Video.MPEG2VIDEO,
		Codec.Video.VP8,
		Codec.Video.VP9,
		Codec.Video.VC1,
	)

	private val containerVideo = arrayOf(
		Codec.Container.`3GP`,
		Codec.Container.ASF,
		Codec.Container.AVI,
		Codec.Container.DVR_MS,
		Codec.Container.ISO,
		Codec.Container.M2V,
		Codec.Container.M4V,
		Codec.Container.MKV,
		Codec.Container.MOV,
		Codec.Container.MP4,
		Codec.Container.MPEG,
		Codec.Container.MPEGTS,
		Codec.Container.OGV,
		Codec.Container.TS,
		Codec.Container.VOB,
		Codec.Container.WEBM,
		Codec.Container.WMV,
		Codec.Container.WTV,
		Codec.Container.XVID,
	)

	private val containerAudio = arrayOf(
		Codec.Audio.APE,
		Codec.Audio.AAC,
//		Codec.Audio.DFF,
//		Codec.Audio.DSF,
		Codec.Audio.FLAC,
		Codec.Audio.MP2,
		Codec.Audio.MP3,
		Codec.Audio.MPA,
		Codec.Audio.OGA,
		Codec.Audio.OGG,
		Codec.Audio.OPUS,
		Codec.Audio.SPX,
		Codec.Audio.PCM,
		Codec.Audio.WAV,
		Codec.Audio.WEBMA,
		Codec.Audio.WMA,
	)

	private val containerPhoto = arrayOf(
		"jpg",
		"jpeg",
		"png",
		"gif",
		"webp",
	)

	init {
		name = "AndroidTV-Zidoo-External"
		maxStaticBitrate = 200_000_000 // 200 mbps
		maxStreamingBitrate = 200_000_000 // 200 mbps
		musicStreamingTranscodingBitrate = 3_584_000 // max server flac bitrate

		directPlayProfiles = arrayOf(
			// Video direct play
			DirectPlayProfile().apply {
				type = DlnaProfileType.Video
				container = containerVideo.joinToString(",")
				videoCodec = buildList {
					addAll(codecsVideo)
					if (ProfileHelper.MediaTest.supportsAv1()) add(Codec.Video.AV1)
				}.joinToString(",")
				audioCodec = forcedAudioCodec ?: buildList {
					addAll(codecsDolby + codecsCommon + codecsPcm + codecsRare)
					if (isDTSEnabled) addAll(codecsDTS)
				}.joinToString(",")
			},
			// Audio direct play
			DirectPlayProfile().apply {
				type = DlnaProfileType.Audio
				container = containerAudio.joinToString(",")
			},
			// Photo direct play
			DirectPlayProfile().apply {
				type = DlnaProfileType.Photo
				container = containerPhoto.joinToString(",")
			}
		)

		transcodingProfiles = arrayOf(

			// NOTE: We get major issues in HLS mode, subs/seek crash the dvd-player
//			TranscodingProfile().apply {
//				type = DlnaProfileType.Video
//				context = EncodingContext.Streaming
//				protocol = "hls"
//				container = Codec.Container.TS
//				videoCodec = buildList {
//					if (ProfileHelper.deviceHevcCodecProfile?.ContainsCodec(Codec.Video.HEVC, Codec.Container.TS) == true) add(Codec.Video.HEVC)
//					add(Codec.Video.H264)
//				}.joinToString(",")
//				audioCodec = forcedAudioCodec ?: buildList {
//					addAll(codecsDolby)
////					if (isDTSEnabled) add(Codec.Audio.DTS)
////					if (isExtraSurroundEnabled) addAll(codecsCommon)
//					add(Codec.Audio.AAC)
//				}.joinToString(",")
//				maxAudioChannels = forceNumChannels?.toString() ?: "8"
////				enableMpegtsM2TsMode = true
////				enableSubtitlesInManifest = true
////				copyTimestamps = true
//				segmentLength = 20
//				minSegments = 0
////				estimateContentLength = true
////				setBreakOnNonKeyFrames(true)
////				requiresPlainVideoItems = true
////				requiresPlainFolders = true
//			},
			// NOTE: seeking will not work in mkv mode!
			TranscodingProfile().apply {
				type = DlnaProfileType.Video
				context = EncodingContext.Streaming
				container = Codec.Container.MKV
				videoCodec = buildList {
					if (ProfileHelper.deviceHevcCodecProfile?.ContainsCodec(Codec.Video.HEVC, Codec.Container.MKV) == true) add(Codec.Video.HEVC)
					add(Codec.Video.H264)
				}.joinToString(",")
				audioCodec = forcedAudioCodec ?: buildList {
					addAll(codecsDolby + codecsPcm)
					if (isDTSEnabled) addAll(codecsDTS)
					if (!forceCompliantSurroundCodecs) addAll(codecsCommon + codecsRare)
				}.joinToString(",")
				copyTimestamps = false
				maxAudioChannels = forceNumChannels?.toString() ?: "8"
			},
			// FLAC transcode audio profile
			TranscodingProfile().apply {
				type = DlnaProfileType.Audio
				context = EncodingContext.Streaming
				container = Codec.Container.FLAC
				audioCodec = Codec.Audio.FLAC
			}
		)

		codecProfiles = listOfNotNull(
			// HEVC profile
			ProfileHelper.deviceHevcCodecProfile,
			// H264 profile
			CodecProfile().apply {
				type = CodecType.Video
				codec = Codec.Video.H264
				conditions = arrayOf(
					ProfileHelper.h264VideoProfileCondition,
					ProfileHelper.h264VideoLevelProfileCondition,
				)
			},
			// Audio-channels: Dolby is 6,8 with atmos at 7.1.4
			ProfileHelper.maxAudioChannelsCodecProfile(codecsDolby + codecsPcm, forceNumChannels ?: 8),
			if (isDTSEnabled) ProfileHelper.maxAudioChannelsCodecProfile(codecsDTS, forceNumChannels ?: 8) else null,
			if (!forceCompliantSurroundCodecs)
				ProfileHelper.maxAudioChannelsCodecProfile(codecsCommon + codecsRare, forceNumChannels ?: 8)
			else
				ProfileHelper.maxAudioChannelsCodecProfile(codecsCommon + codecsRare, 2)
		).toTypedArray()

		subtitleProfiles = arrayOf(
			subtitleProfile(Codec.Subtitle.ASS, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.SSA, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.SRT, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.SUBRIP, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.PGS, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.PGSSUB, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.DVDSUB, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.VTT, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.SUB, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.IDX, SubtitleDeliveryMethod.Embed),
			subtitleProfile(Codec.Subtitle.SMI, SubtitleDeliveryMethod.Embed),
			// side-car subtitle files, passed to the Zidoo player as urls (see ExternalPlayerActivity.addZidooSubtitleExtras)
			subtitleProfile(Codec.Subtitle.SRT, SubtitleDeliveryMethod.External),
			subtitleProfile(Codec.Subtitle.SUBRIP, SubtitleDeliveryMethod.External),
			subtitleProfile(Codec.Subtitle.ASS, SubtitleDeliveryMethod.External),
			subtitleProfile(Codec.Subtitle.SSA, SubtitleDeliveryMethod.External),
		)
	}
}
