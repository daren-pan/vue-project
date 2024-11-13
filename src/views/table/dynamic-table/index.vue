<template>
  <div class="app-container">
    <el-upload class="upload-demo" drag action="https://jsonplaceholder.typicode.com/posts/" multiple>
      <i class="el-icon-upload" />
      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      <div slot="tip" class="el-upload__tip">只能上传yaml文件，且不超过500kb</div>
    </el-upload>
  </div>
</template>

<script>
import FixedThead from './components/FixedThead'
import UnfixedThead from './components/UnfixedThead'

export default {
  name: 'DynamicTable',
  components: { FixedThead, UnfixedThead },
  data() {
    return {
      tableData: [] // 初始为空数组，将在 mounted 时获取数据
    }
  }
}
</script>
