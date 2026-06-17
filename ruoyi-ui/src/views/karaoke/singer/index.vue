<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="歌手名称" prop="singerName">
        <el-input
          v-model="queryParams.singerName"
          placeholder="请输入歌手名称"
          clearable
          style="width: 180px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="地区" prop="region">
        <el-select v-model="queryParams.region" placeholder="请选择地区" clearable style="width: 140px">
          <el-option
            v-for="dict in dict.type.karaoke_singer_region"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="性别" prop="gender">
        <el-select v-model="queryParams.gender" placeholder="请选择性别" clearable style="width: 120px">
          <el-option
            v-for="dict in dict.type.sys_user_sex"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
          <el-option
            v-for="dict in dict.type.sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="parseInt(dict.value)"
          />
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
        >新增歌手</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" align="center" width="55"/>
      <el-table-column label="ID" align="center" prop="id" width="60"/>
      <el-table-column label="头像" align="center" prop="singerAvatar" width="80">
        <template slot-scope="scope">
          <el-image
            v-if="scope.row.singerAvatar"
            :src="scope.row.singerAvatar"
            :preview-src-list="[scope.row.singerAvatar]"
            fit="cover"
            style="width: 40px; height: 40px; border-radius: 50%"
          />
          <el-avatar v-else :size="40">{{ (scope.row.singerName || '').charAt(0) }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column label="歌手名称" align="center" prop="singerName" :show-overflow-tooltip="true"/>
      <el-table-column label="地区" align="center" prop="region" width="100">
        <template slot-scope="scope">
          <el-tag :type="regionTagType(scope.row.region)">{{ selectDictLabel(dict.type.karaoke_singer_region, scope.row.region) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="性别" align="center" prop="gender" width="80">
        <template slot-scope="scope">
          <el-tag :type="scope.row.gender === 0 ? 'primary' : (scope.row.gender === 1 ? 'danger' : 'info')">
            {{ selectDictLabel(dict.type.sys_user_sex, scope.row.gender) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="出生年月" align="center" prop="birthday" width="120">
        <template slot-scope="scope">
          <span>{{ scope.row.birthday || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="拼音首字母" align="center" prop="pinyinInitials" width="100"/>
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">
            {{ selectDictLabel(dict.type.sys_normal_disable, scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" align="center" prop="updateTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.updateTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
          >编辑</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
          >删除</el-button>
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

    <!-- 添加/编辑歌手对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="歌手名称" prop="singerName">
          <el-input v-model="form.singerName" placeholder="请输入歌手名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="头像" prop="singerAvatar">
          <el-upload
            :limit="1"
            accept=".jpg,.jpeg,.png"
            :action="upload.url"
            :headers="upload.headers"
            :file-list="upload.fileList"
            :on-success="handleAvatarSuccess"
            :on-remove="handleAvatarRemove"
            :auto-upload="true">
            <el-button slot="trigger" size="small" type="primary">上传头像</el-button>
            <div slot="tip" class="el-upload__tip">支持 jpg/png 格式</div>
          </el-upload>
          <el-image
            v-if="form.singerAvatar"
            :src="form.singerAvatar"
            :preview-src-list="[form.singerAvatar]"
            fit="cover"
            style="width: 80px; height: 80px; border-radius: 50%; margin-top: 8px"
          />
        </el-form-item>
        <el-form-item label="地区" prop="region">
          <el-select v-model="form.region" placeholder="请选择地区" style="width: 100%">
            <el-option
              v-for="dict in dict.type.karaoke_singer_region"
              :key="dict.value"
              :label="dict.label"
              :value="parseInt(dict.value)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio
              v-for="dict in dict.type.sys_user_sex"
              :key="dict.value"
              :label="parseInt(dict.value)"
            >{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生年月" prop="birthday">
          <el-date-picker
            v-model="form.birthday"
            type="date"
            placeholder="选择出生日期"
            value-format="yyyy-MM-dd"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="拼音首字母" prop="pinyinInitials">
          <el-input v-model="form.pinyinInitials" placeholder="如：ZQL（周杰伦）" maxlength="10" />
        </el-form-item>
        <el-form-item label="排序号" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in dict.type.sys_normal_disable"
              :key="dict.value"
              :label="parseInt(dict.value)"
            >{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="歌手简介"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSinger, addSinger, updateSinger, delSinger } from "@/api/karaoke/manage";
import { parseTime } from "@/utils/ruoyi";
import { getToken } from "@/utils/auth";

export default {
  name: "SingerList",
  dicts: ['karaoke_singer_region', 'sys_user_sex', 'sys_normal_disable'],
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
      // 上传参数
      upload: {
        headers: { Authorization: "Bearer " + getToken() },
        url: process.env.VUE_APP_BASE_API + "/common/upload",
        fileList: []
      },
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        singerName: '',
        region: undefined,
        gender: undefined,
        status: undefined
      },
      form: {},
      rules: {
        singerName: [
          { required: true, message: "请输入歌手名称", trigger: "blur" }
        ],
        region: [
          { required: true, message: "请选择地区", trigger: "change" }
        ],
        gender: [
          { required: true, message: "请选择性别", trigger: "change" }
        ]
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    parseTime,

    // 地区标签颜色
    regionTagType(region) {
      const map = { 0: 'info', 1: 'success', 2: 'warning', 3: 'primary', 4: 'danger', 5: '' };
      return map[region] || 'info';
    },

    // 查询列表
    getList() {
      this.loading = true;
      listSinger(this.queryParams).then(response => {
        this.dataList = response.rows || [];
        this.total = response.total;
        this.loading = false;
      }).catch(() => { this.loading = false; });
    },

    // 搜索
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },

    // 选择
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },

    // 新增
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "新增歌手";
    },

    // 修改
    handleUpdate(row) {
      this.reset();
      this.form = { ...row };
      this.open = true;
      this.title = "修改歌手";
    },

    // 头像上传成功
    handleAvatarSuccess(response) {
      if (response.code === 200) {
        this.form.singerAvatar = response.fileName;
        this.$modal.msgSuccess("上传成功");
      }
    },
    handleAvatarRemove() {
      this.form.singerAvatar = '';
    },

    // 表单重置
    reset() {
      this.form = {
        id: undefined,
        singerName: '',
        singerAvatar: '',
        region: 1,
        gender: 0,
        birthday: null,
        pinyinInitials: '',
        sortOrder: 0,
        status: 0,
        description: ''
      };
      this.upload.fileList = [];
      this.resetForm("form");
    },

    // 提交
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id !== undefined) {
            updateSinger(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addSinger(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },

    // 删除
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal.confirm('是否确认删除编号为"' + ids + '"的数据项？').then(() => {
        return delSinger(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },

    cancel() {
      this.open = false;
      this.reset();
    }
  }
};
</script>
