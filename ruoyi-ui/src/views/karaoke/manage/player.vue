<template>
  <div class="player-page">
    <!-- 顶部信息栏 -->
    <div class="player-header">
      <span class="header-title">{{ song.songTitle || '未加载' }}</span>
      <span class="header-singer">{{ song.singerName }}</span>
      <span class="header-time">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
      <span class="header-status" :class="{ playing: isPlaying, paused: !isPlaying }">
        {{ isPlaying ? '播放中' : '已暂停' }}
      </span>
    </div>

    <!-- 视频画面 -->
    <div class="video-wrapper">
      <video
        ref="videoPlayer"
        class="player-video"
        :class="{ 'is-hidden': !hasVideo }"
        :src="videoSrc"
        preload="auto"
        controls
        @timeupdate="onTimeUpdate"
        @loadedmetadata="onVideoReady"
        @ended="onSongEnd"
        @error="onVideoError"
        @play="onVideoPlay"
        @pause="onVideoPause"
        @seeking="onVideoSeeking"
        @seeked="onVideoSeeked"
        crossorigin="anonymous"
      ></video>
      <div v-if="!hasVideo" class="video-fallback">
        <i class="el-icon-headset" style="font-size:64px;color:#999"></i>
        <div class="fallback-title">{{ song.songTitle || '未选择歌曲' }}</div>
        <div class="fallback-singer">{{ song.singerName }}</div>
      </div>
    </div>

    <!-- 控制按钮 -->
    <div class="controls">
      <div class="btn-group">
        <el-button icon="el-icon-d-arrow-left" @click="playPrevious" :disabled="!hasPrevious">上一曲</el-button>
        <el-button v-if="!isPlaying" type="primary" icon="el-icon-video-play" @click="togglePlay">播放</el-button>
        <el-button v-else type="warning" icon="el-icon-video-pause" @click="togglePlay">暂停</el-button>
        <el-button icon="el-icon-d-arrow-right" @click="playNext" :disabled="!hasNext">下一曲</el-button>
      </div>
      <div class="btn-group">
        <el-button @click="toggleVocal" :type="vocalEnabled ? 'success' : 'info'" icon="el-icon-mic">原唱</el-button>
        <el-button icon="el-icon-refresh" @click="reSing">重唱</el-button>
        <el-button type="danger" icon="el-icon-close" @click="closePlayer">关闭</el-button>
      </div>
    </div>

    <!-- 音量调节 -->
    <div class="volume-controls">
      <div class="volume-row">
        <span class="volume-label">伴奏音量</span>
        <el-slider v-model="accompVolume" :min="0" :max="100" class="volume-slider"></el-slider>
        <span class="volume-value">{{ accompVolume }}</span>
      </div>
      <div class="volume-row">
        <span class="volume-label">人声音量</span>
        <el-slider v-model="vocalVolume" :min="0" :max="100" class="volume-slider"></el-slider>
        <span class="volume-value">{{ vocalVolume }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import { listMV } from "@/api/karaoke/manage";

export default {
  name: "karaokePlayer",
  props: {
    songId: {
      type: [Number, String],
      default: null
    }
  },
  data() {
    return {
      song: { songTitle: "", singerName: "", videoPath: "", accompanimentPath: "", vocalsPath: "" },
      isPlaying: false,
      currentTime: 0,
      duration: 0,
      accompVolume: 80,
      vocalVolume: 80,
      vocalEnabled: true,
      hasVideo: true,
      videoSrc: "",
      accompPlayer: null,
      vocalPlayer: null,
      playableList: [],
      playIndex: -1,
      // WebSocket
      websock: null,
      wsConnected: false,
      positionTimer: null,
    };
  },
  computed: {
    hasPrevious() { return this.playIndex > 0; },
    hasNext() { return this.playIndex < this.playableList.length - 1; },
  },
  watch: {
    accompVolume(val) { if (this.accompPlayer) this.accompPlayer.volume = val / 100; },
    vocalVolume(val) { if (this.vocalPlayer) this.vocalPlayer.volume = val / 100; },
    songId: {
      handler(newId) {
        if (newId) this.loadSong(parseInt(newId));
      },
      immediate: true
    }
  },
  created() {
    this.initWebSocket();
    this.loadPlayableList().then(() => {
      if (this.songId) this.loadSong(parseInt(this.songId));
    });
  },
  beforeDestroy() {
    this.stopPositionSync();
    this.closeWebSocket();
    this.releasePlayer();
  },
  methods: {
    // ==================== 数据加载 ====================

    loadPlayableList() {
      return listMV({ pageNum: 1, pageSize: 500 }).then(res => {
        this.playableList = (res.rows || []).filter(item => item.status === 1);
      }).catch(() => {
        this.playableList = [];
      });
    },

    loadSong(songId) {
      let row = this.playableList.find(item => item.id === songId);
      if (row) {
        this.openSong(row);
      } else {
        // 可能不在已加载的列表中，重新查询
        listMV({ pageNum: 1, pageSize: 500 }).then(res => {
          this.playableList = (res.rows || []).filter(item => item.status === 1);
          row = this.playableList.find(item => item.id === songId);
          if (row) this.openSong(row);
        });
      }
    },

    openSong(row) {
      this.playIndex = this.playableList.findIndex(item => item.id === row.id);
      this.song = {
        songTitle: row.songTitle || "",
        singerName: row.singerName || "",
        videoPath: row.videoPath || "",
        accompanimentPath: row.accompanimentPath || "",
        vocalsPath: row.vocalsPath || "",
      };
      this.currentTime = 0;
      this.duration = 0;
      this.isPlaying = false;
      this.loadMedia();
    },

    loadMedia() {
      const baseUrl = process.env.VUE_APP_BASE_API || "";

      // 释放旧播放器
      if (this.accompPlayer) { this.accompPlayer.pause(); this.accompPlayer = null; }
      if (this.vocalPlayer) { this.vocalPlayer.pause(); this.vocalPlayer = null; }

      // 创建音频播放器
      const accPath = baseUrl + (this.song.accompanimentPath || "");
      const vocPath = baseUrl + (this.song.vocalsPath || "");
      this.accompPlayer = new Audio(accPath);
      this.vocalPlayer = new Audio(vocPath);
      this.accompPlayer.volume = this.accompVolume / 100;
      this.vocalPlayer.volume = this.vocalEnabled ? this.vocalVolume / 100 : 0;

      // 音频加载错误处理
      this.accompPlayer.onerror = () => console.warn("伴奏加载失败:", accPath);
      this.vocalPlayer.onerror = () => console.warn("人声加载失败:", vocPath);

      // 设置视频源
      if (this.song.videoPath) {
        this.videoSrc = baseUrl + this.song.videoPath;
        console.log(this.videoSrc)
        this.hasVideo = true;
      } else {
        this.hasVideo = false;
        this.videoSrc = "";
      }

      // 加载并自动播放（浏览器可能拦截，用户可手动点击播放）
      this.$nextTick(() => {
        const video = this.$refs.videoPlayer;
        if (video) {
          video.load();
          video.play().then(() => {
            if (this.accompPlayer) {
              this.accompPlayer.currentTime = 0;
              this.accompPlayer.play().catch(() => {});
            }
            if (this.vocalPlayer) {
              this.vocalPlayer.currentTime = 0;
              this.vocalPlayer.play().catch(() => {});
            }
            this.isPlaying = true;
            this.startPositionSync();
          }).catch(() => {
            // 浏览器拦截自动播放，用户需手动点击播放按钮
            this.isPlaying = false;
          });
        }
      });
    },

    // ==================== 视频事件 ====================

    onVideoReady() {
      const v = this.$refs.videoPlayer;
      if (v) this.duration = v.duration || 0;
    },

    onTimeUpdate() {
      const v = this.$refs.videoPlayer;
      if (!v) return;
      this.currentTime = v.currentTime;
      // 持续同步音频位置到视频
      const vt = v.currentTime;
      if (this.accompPlayer && Math.abs(this.accompPlayer.currentTime - vt) > 0.1) {
        this.accompPlayer.currentTime = vt;
      }
      if (this.vocalPlayer && Math.abs(this.vocalPlayer.currentTime - vt) > 0.1) {
        this.vocalPlayer.currentTime = vt;
      }
    },

    onVideoPlay() {
      this.isPlaying = true;
      if (this.accompPlayer) this.accompPlayer.play();
      if (this.vocalPlayer) this.vocalPlayer.play();
      this.startPositionSync();
    },

    onVideoPause() {
      this.isPlaying = false;
      if (this.accompPlayer) this.accompPlayer.pause();
      if (this.vocalPlayer) this.vocalPlayer.pause();
      this.stopPositionSync();
    },

    onVideoSeeking() {
      const ct = this.$refs.videoPlayer?.currentTime;
      if (this.accompPlayer) this.accompPlayer.currentTime = ct;
      if (this.vocalPlayer) this.vocalPlayer.currentTime = ct;
    },

    onVideoSeeked() {
      const ct = this.$refs.videoPlayer?.currentTime;
      if (this.accompPlayer && Math.abs(this.accompPlayer.currentTime - ct) > 0.1) this.accompPlayer.currentTime = ct;
      if (this.vocalPlayer && Math.abs(this.vocalPlayer.currentTime - ct) > 0.1) this.vocalPlayer.currentTime = ct;
    },

    onVideoError() { this.hasVideo = false; },

    onSongEnd() {
      if (this.accompPlayer) this.accompPlayer.pause();
      if (this.vocalPlayer) this.vocalPlayer.pause();
      this.stopPositionSync();
      if (this.hasNext) {
        const next = this.playableList[this.playIndex + 1];
        this.openSong(next);
      }
    },

    // ==================== 控制操作 ====================

    togglePlay() {
      const video = this.$refs.videoPlayer;
      if (this.isPlaying) {
        if (video) video.pause();
        if (this.accompPlayer) this.accompPlayer.pause();
        if (this.vocalPlayer) this.vocalPlayer.pause();
        this.isPlaying = false;
      } else {
        if (this.accompPlayer) { this.accompPlayer.currentTime = this.currentTime; this.accompPlayer.play(); }
        if (this.vocalPlayer) { this.vocalPlayer.currentTime = this.currentTime; this.vocalPlayer.play(); }
        if (video) { video.currentTime = this.currentTime; video.play().catch(() => {}); }
        this.isPlaying = true;
      }
    },

    reSing() {
      this.seekTo(0);
      if (!this.isPlaying) {
        const video = this.$refs.videoPlayer;
        if (video) video.play().catch(() => {});
        if (this.accompPlayer) this.accompPlayer.play();
        if (this.vocalPlayer) this.vocalPlayer.play();
        this.isPlaying = true;
      }
    },

    toggleVocal() {
      this.vocalEnabled = !this.vocalEnabled;
      if (this.vocalPlayer) {
        this.vocalPlayer.volume = this.vocalEnabled ? this.vocalVolume / 100 : 0;
      }
    },

    seekTo(val) {
      const video = this.$refs.videoPlayer;
      if (video) video.currentTime = val;
      if (this.accompPlayer) this.accompPlayer.currentTime = val;
      if (this.vocalPlayer) this.vocalPlayer.currentTime = val;
    },

    playNext() {
      if (this.hasNext) {
        const next = this.playableList[this.playIndex + 1];
        this.releasePlayer();
        this.openSong(next);
      }
    },

    playPrevious() {
      if (this.hasPrevious) {
        const prev = this.playableList[this.playIndex - 1];
        this.releasePlayer();
        this.openSong(prev);
      }
    },

    closePlayer() {
      this.releasePlayer();
      this.$router.back();
    },

    releasePlayer() {
      this.isPlaying = false;
      this.stopPositionSync();
      const video = this.$refs.videoPlayer;
      if (video) video.pause();
      if (this.accompPlayer) { this.accompPlayer.pause(); this.accompPlayer = null; }
      if (this.vocalPlayer) { this.vocalPlayer.pause(); this.vocalPlayer = null; }
      this.videoSrc = "";
    },

    // ==================== 进度同步 ====================

    startPositionSync() {
      this.stopPositionSync();
      this.positionTimer = setInterval(() => {
        if (!this.wsConnected || !this.websock) return;
        this.broadcastWs(10, {
          position: this.$refs.videoPlayer?.currentTime || this.currentTime,
          playing: this.isPlaying,
          duration: this.duration,
          songId: this.playableList[this.playIndex]?.id,
        });
      }, 1000);
    },

    stopPositionSync() {
      if (this.positionTimer) { clearInterval(this.positionTimer); this.positionTimer = null; }
    },

    // ==================== WebSocket ====================

    initWebSocket() {
      // 开发环境 WebSocket 直接连后端 8080，生产环境同域
      const host = process.env.NODE_ENV === 'development' ? 'localhost:8080' : location.host;
      const proto = location.protocol === "https:" ? "wss:" : "ws:";
      const wsUrl = proto + "//" + host + "/ws/1";
      try {
        this.websock = new WebSocket(wsUrl);
        this.websock.onopen = () => { this.wsConnected = true; };
        this.websock.onmessage = (event) => {
          if (!event.data) return;
          try { this.handleWsMessage(JSON.parse(event.data)); } catch (e) {}
        };
        this.websock.onclose = () => { this.wsConnected = false; };
        this.websock.onerror = () => { this.wsConnected = false; };
      } catch (e) {}
    },

    closeWebSocket() {
      if (this.websock) { this.websock.close(); this.wsConnected = false; }
    },

    broadcastWs(code, data) {
      if (!this.wsConnected || !this.websock) return;
      try { this.websock.send(JSON.stringify({ code: code, data: data, message: "" })); } catch (e) {}
    },

    handleWsMessage(msg) {
      if (!msg) return;
      const v = this.$refs.videoPlayer;
      const a = this.accompPlayer;
      const vo = this.vocalPlayer;
      switch (msg.code) {
        case 1: case "1": // 开始
          if (!this.isPlaying) {
            if (a) { a.currentTime = this.currentTime; a.play(); }
            if (vo) { vo.currentTime = this.currentTime; vo.play(); }
            if (v) { v.currentTime = this.currentTime; v.play().catch(() => {}); }
            this.isPlaying = true;
          }
          break;
        case 2: case "2": // 停止
          if (v) v.pause();
          if (a) a.pause();
          if (vo) vo.pause();
          this.isPlaying = false;
          break;
        case 3: case "3": // 重唱
          this.reSing();
          break;
        case 4: case "4": // 开启原唱
          this.vocalEnabled = true;
          if (vo) vo.volume = this.vocalVolume / 100;
          break;
        case 5: case "5": // 关闭原唱
          this.vocalEnabled = false;
          if (vo) vo.volume = 0;
          break;
        case 6: case "6": // 音量
          try {
            const vol = typeof msg.data === "string" ? JSON.parse(msg.data) : msg.data;
            if (vol.accompanimentVolume !== undefined) this.accompVolume = vol.accompanimentVolume;
            if (vol.vocalsVolume !== undefined) this.vocalVolume = vol.vocalsVolume;
          } catch (e) {}
          break;
        case 8: case "8": // 切歌
          this.playNext();
          break;
      }
    },

    formatTime(seconds) {
      if (!seconds || isNaN(seconds)) return "00:00";
      const m = Math.floor(seconds / 60);
      const s = Math.floor(seconds % 60);
      return (m < 10 ? "0" + m : m) + ":" + (s < 10 ? "0" + s : s);
    },
  },
};
</script>

<style scoped>
.player-page {
  padding: 20px;
  background: #0a0a1a;
  min-height: 100vh;
  color: #ccc;
}

.player-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 12px 20px;
  background: #1a1a2e;
  border-radius: 8px;
}

.header-title { font-size: 22px; font-weight: bold; color: #fff; }
.header-singer { font-size: 16px; color: #999; }
.header-time { font-size: 14px; color: #888; margin-left: auto; font-family: monospace; }
.header-status { font-size: 14px; padding: 2px 12px; border-radius: 10px; }
.header-status.playing { color: #67C23A; background: rgba(103,194,58,0.15); }
.header-status.paused { color: #E6A23C; background: rgba(230,162,60,0.15); }

.video-wrapper {
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.player-video { width: 100%; max-height: 65vh; display: block; }
.player-video.is-hidden { display: none; }

.video-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  background: linear-gradient(135deg, #1a1a2e, #16213e, #0f3460);
  color: #fff;
  gap: 16px;
}

.fallback-title { font-size: 24px; font-weight: bold; color: #e0e0e0; }
.fallback-singer { font-size: 16px; color: #999; }

.progress-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
}

.progress-slider { flex: 1; }
.time-label { font-size: 13px; color: #888; min-width: 48px; text-align: center; font-family: monospace; }

.controls {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin: 16px 0;
  flex-wrap: wrap;
}

.btn-group { display: flex; gap: 8px; }

.volume-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 40px;
  margin-top: 16px;
}

.volume-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.volume-label { font-size: 14px; color: #aaa; min-width: 70px; }
.volume-slider { flex: 1; }
.volume-value { font-size: 13px; color: #888; min-width: 30px; text-align: right; }
</style>
