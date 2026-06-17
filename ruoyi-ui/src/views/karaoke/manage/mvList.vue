<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="100px">

      <el-form-item label="名称" prop="songTitle">
        <el-input
            v-model="queryParams.songTitle"
            placeholder="请输入名称"
            clearable
            style="width: 150px"
            @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="歌手" prop="singerName">
        <el-input
            v-model="queryParams.singerName"
            placeholder="请输入歌手"
            clearable
            style="width: 150px"
            @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
          <el-option label="处理中" :value="0" />
          <el-option label="已完成" :value="1" />
          <el-option label="失败" :value="2" />
          <el-option label="待解析" :value="3" />
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
            type="primary"
            plain
            icon="el-icon-plus"
            size="mini"
            @click="handleAdd"
        >导入MV
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="el-icon-cpu"
            size="mini"
            :disabled="multiple"
            @click="handleParseSelected"
        >选定解析
        </el-button>
      </el-col>

      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" align="center"/>
      <el-table-column label="ID" align="center" prop="id" width="60"/>
      <el-table-column label="MV名称" align="center" prop="songTitle" :show-overflow-tooltip="true"/>
      <el-table-column label="歌手" align="center" prop="singerNames" :show-overflow-tooltip="true"/>
      <el-table-column label="状态" align="center" prop="status" width="120">
        <template slot-scope="scope">
          <span v-if="scope.row.status === 1" style="color:#67C23A">已完成</span>
          <span v-else-if="scope.row.status === 2" style="color:#F56C6C">失败</span>
          <span v-else-if="scope.row.status === 3" style="color:#909399">待解析</span>
          <span v-else style="color:#E6A23C">处理中 {{ scope.row.process || 0 }}%</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" align="center" prop="updateTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.updateTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" align="center" width="380" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
              size="mini"
              type="text"
              icon="el-icon-video-play"
              @click="goPlayer(scope.row)"
              :disabled="scope.row.status !== 1"
          >播放
          </el-button>
          <el-button
              size="mini"
              type="text"
              icon="el-icon-headset"
              @click="playAudioTrack(scope.row, 'accompaniment')"
              :disabled="scope.row.status !== 1 || !scope.row.accompanimentPath"
          >伴奏
          </el-button>
          <el-button
              size="mini"
              type="text"
              icon="el-icon-microphone"
              @click="playAudioTrack(scope.row, 'vocals')"
              :disabled="scope.row.status !== 1 || !scope.row.vocalsPath"
          >人声
          </el-button>
          <el-button
              size="mini"
              type="text"
              icon="el-icon-cpu"
              @click="handleParse(scope.row)"
          >{{ scope.row.status === 1 ? '重新解析' : '解析' }}
          </el-button>
          <el-button
              size="mini"
              type="text"
              icon="el-icon-edit"
              @click="handleUpdate(scope.row)"
          >编辑
          </el-button>
          <el-button
              size="mini"
              type="text"
              icon="el-icon-delete"
              @click="handleDelete(scope.row)"
          >删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
        v-show="total>0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
    />

    <!-- 上传对话框 -->
    <el-dialog :title="title" :visible.sync="open" append-to-body width="600px">
      <el-form ref="form" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="MV名称" prop="songTitle">
          <el-input v-model="form.songTitle" placeholder="可不填，默认取文件名；批量上传时建议后续编辑" />
        </el-form-item>
        <el-form-item label="歌手" prop="singerIds">
          <el-select v-model="form.singerIds" multiple filterable placeholder="请选择歌手" style="width: 100%">
            <el-option
              v-for="item in singerOptions"
              :key="item.id"
              :label="item.singerName"
              :value="item.id"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="导入文件" prop="file">
          <el-upload
              ref="upload"
              multiple
              :limit="50"
              accept=".mp4,.mov,.avi"
              :action="upload.url"
              :headers="upload.headers"
              :file-list="upload.fileList"
              :on-progress="handleFileUploadProgress"
              :on-success="handleFileSuccess"
              :on-error="handleFileError"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :auto-upload="false">
            <el-button slot="trigger" size="small" type="primary">选取文件</el-button>
            <el-button style="margin-left: 10px;" size="small" type="success" :loading="upload.isUploading" @click="submitUpload">上传到服务器</el-button>
            <div slot="tip" class="el-upload__tip">支持 mp4 / mov / avi 格式，可一次选择多个；上传后点击确定仅入库，不会自动解析</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 播放弹窗 -->
    <el-dialog
        title="MV播放器"
        :visible.sync="playerVisible"
        width="90%"
        top="5vh"
        append-to-body
        :close-on-click-modal="false"
        destroy-on-close
    >
      <player v-if="playerVisible" :song-id="currentSongId" @close="playerVisible = false" />
    </el-dialog>

    <!-- 音轨试听弹窗 -->
    <el-dialog
        :title="audioPreviewTitle"
        :visible.sync="audioPreviewVisible"
        width="520px"
        append-to-body
        destroy-on-close
        @close="closeAudioPreview"
    >
      <div class="audio-preview">
        <div class="audio-preview-name">{{ audioPreviewSong }}</div>
        <audio
            v-if="audioPreviewUrl"
            ref="audioPreview"
            :src="audioPreviewUrl"
            controls
            autoplay
            class="audio-preview-player"
        ></audio>
      </div>
    </el-dialog>

  </div>
</template>

<script>
import {delMV, listMV, updateMV, uploadMV, uploadMVBatch, parseMV, listSinger} from "@/api/karaoke/manage";
import {parseTime} from "@/utils/ruoyi";
import {getToken} from "@/utils/auth";
import player from "./player";

export default {
  name: "mvList",
  components: { player },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      dataList: [],
      title: "",
      open: false,
      // 播放弹窗
      playerVisible: false,
      currentSongId: null,
      // 音轨试听
      audioPreviewVisible: false,
      audioPreviewTitle: "",
      audioPreviewSong: "",
      audioPreviewUrl: "",
      // 歌手选项
      singerOptions: [],
      dateRange: [],
      // 上传参数
      upload: {
        isUploading: false,
        headers: { Authorization: "Bearer " + getToken() },
        url: process.env.VUE_APP_BASE_API + "/common/upload",
        fileList: [],
        uploadedFiles: []
      },
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        songTitle: '',
        singerName: '',
        status: undefined
      },
      form: {},
      rules: {
      },
    };
  },
  created() {
    this.getList();
    this.getSingerList();
  },
  methods: {
    parseTime,
    // 获取歌手列表
    getSingerList() {
      listSinger({ pageNum: 1, pageSize: 500, status: 0 }).then(res => {
        this.singerOptions = res.rows || [];
      });
    },

    goPlayer(row) {
      this.currentSongId = row.id;
      this.playerVisible = true;
    },

    playAudioTrack(row, type) {
      const isVocals = type === "vocals";
      const path = isVocals ? row.vocalsPath : row.accompanimentPath;
      if (!path) {
        this.$modal.msgError(isVocals ? "暂无人声音轨" : "暂无伴奏音轨");
        return;
      }

      const trackName = isVocals ? "人声" : "伴奏";
      this.audioPreviewTitle = trackName + "试听";
      this.audioPreviewSong = (row.songTitle || "未命名MV") + (row.singerNames ? " - " + row.singerNames : "");
      this.audioPreviewUrl = this.toResourceUrl(path);
      this.audioPreviewVisible = true;

      this.$nextTick(() => {
        const audio = this.$refs.audioPreview;
        if (audio) {
          audio.load();
          audio.play().catch(() => {});
        }
      });
    },

    closeAudioPreview() {
      const audio = this.$refs.audioPreview;
      if (audio) {
        audio.pause();
        audio.currentTime = 0;
      }
      this.audioPreviewUrl = "";
    },

    toResourceUrl(path) {
      if (!path) {
        return "";
      }
      if (/^https?:\/\//i.test(path)) {
        return path;
      }
      return (process.env.VUE_APP_BASE_API || "") + path;
    },

    // ==================== 上传 / 查询 ====================

    submitUpload() {
      this.$refs.upload.submit();
    },
    handleFileUploadProgress(event, file, fileList) {
      this.upload.isUploading = true;
    },
    handleFileSuccess(response, file, fileList) {
      const item = {
        fileName: response.fileName,
        originalFilename: response.originalFilename || file.name,
        songTitle: this.form.songTitle || "",
        singerIds: this.form.singerIds || []
      };
      const exists = this.upload.uploadedFiles.some(fileItem => fileItem.fileName === item.fileName);
      if (!exists) {
        this.upload.uploadedFiles.push(item);
      }
      this.upload.isUploading = fileList.some(item => item.status === "uploading");
      this.$modal.msgSuccess((response.originalFilename || file.name) + " 上传成功");
    },
    handleFileError() {
      this.upload.isUploading = false;
      this.$modal.msgError("文件上传失败");
    },
    handleFileChange(file, fileList) {
      this.upload.fileList = fileList;
    },
    handleFileRemove(file, fileList) {
      this.upload.fileList = fileList;
      this.upload.uploadedFiles = this.upload.uploadedFiles.filter(item => item.originalFilename !== file.name);
    },
    getList() {
      this.loading = true;
      listMV(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
            this.dataList = response.rows;
            this.total = response.total;
            this.loading = false;
          }
      );
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {
        songTitle: '',
        singerIds: [],
        fileName: '',
        originalFilename: ''
      };
      this.upload.uploadedFiles = [];
      this.resetForm("form");
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.dateRange = [];
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handleAdd() {
      this.reset();
      this.upload.fileList = [];
      this.upload.uploadedFiles = [];
      this.open = true;
      this.title = "添加MV";
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleUpdate(row) {
      this.reset();
      // 设置当前行的数据到表单
      this.form = {
        id: row.id,
        songTitle: row.songTitle || '',
        singerIds: row.singerIds ? row.singerIds.split(',').map(Number) : [],
        sourceVideoPath: row.sourceVideoPath
      };
      this.title = "修改MV";
      this.open = true;
    },
    handleParse(row) {
      const id = row.id;
      this.$modal.confirm('是否确认将 "' + (row.songTitle || id) + '" 加入解析队列？').then(() => {
        return parseMV([id]);
      }).then(() => {
        this.$modal.msgSuccess("已加入解析队列");
        this.getList();
      }).catch(() => {});
    },
    handleParseSelected() {
      this.$modal.confirm('是否确认将选中的 ' + this.ids.length + ' 个MV加入解析队列？').then(() => {
        return parseMV(this.ids);
      }).then(() => {
        this.$modal.msgSuccess("已加入解析队列");
        this.getList();
      }).catch(() => {});
    },
    submitForm: function () {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id !== undefined) {
            // 数组转成逗号分隔字符串传给后端
            const submitData = {
              ...this.form,
              singerIds: (this.form.singerIds || []).join(',')
            };
            updateMV(submitData).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            if (!this.upload.uploadedFiles.length) {
              this.$modal.msgError("请先上传MV文件");
              return;
            }
            const files = this.upload.uploadedFiles.map(file => ({
              ...file,
              songTitle: this.form.songTitle || file.songTitle || "",
              singerIds: (this.form.singerIds || file.singerIds || []).join(',')
            }));
            const request = files.length === 1 ? uploadMV(files[0]) : uploadMVBatch(files);
            request.then(response => {
              this.$modal.msgSuccess(files.length === 1 ? "导入成功，等待选定解析" : response.msg || "批量导入成功，等待选定解析");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    handleDelete(row) {
      const id = row.id || this.ids;
      this.$modal.confirm('是否确认删除参数编号为"' + id + '"的数据项？').then(function () {
        return delMV(id);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {
      });
    },


  }
};
</script>

<style scoped>
.audio-preview {
  padding: 4px 0 10px;
}

.audio-preview-name {
  margin-bottom: 12px;
  color: #606266;
  font-size: 14px;
}

.audio-preview-player {
  width: 100%;
}
</style>
