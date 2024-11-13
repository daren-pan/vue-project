<template>
  <div class="app-container documentation-container">
    <iframe src="http://localhost:3030/d/rYdddlPWk/node-exporter-full" frameborder="0" name="popup" class="web" />
  </div>
</template>

<script>
import DropdownMenu from '@/components/Share/DropdownMenu'

export default {
  name: 'Documentation',
  components: { DropdownMenu },
  data() {
    return {

    }
  }
}
</script>

<style lang="scss" scoped>
.documentation-container {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  padding: 0px;

  .document-btn {
    flex-shrink: 0;
    display: block;
    cursor: pointer;
    background: black;
    color: white;
    height: 60px;
    padding: 0 16px;
    margin: 16px;
    line-height: 60px;
    font-size: 20px;
    text-align: center;
  }

  .web {
    display: inline;
    width: 100%;
    height: 90vh;
  }
}
</style>
