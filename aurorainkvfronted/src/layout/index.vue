<template>
  <div class="h-100vh w-100vw">
    <el-container>
      <!-- 头部 -->
      <el-header class="header">
        <span>AurorainKV控制台</span>
      </el-header>

      <!-- 主体 -->
      <el-container class="h-100% w-100%">
        <!-- 侧边栏 -->
        <el-aside width="230px" class="aside">
          <NavigationBar />
        </el-aside>

        <!-- 内容区域 -->
        <el-main class="main">
          <RouterView />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import NavigationBar from './components/navigation.vue'
</script>

<style scoped>
/* 头部样式 */
.header {
  height: 100px;
  background-color: #3C4E60;
  display: flex;
  align-items: center;
  font-family: 'SourceHanSansCN-Medium';
  font-size: 24px;
  color: #ffffff;
  padding-left: 20px;
}

/* 侧边栏样式 */
.aside {
  background-color: #3C4E60; /* 与菜单背景色一致 */
  height: calc(100vh - 100px); /* 减去头部高度 */
}

/* 内容区域样式 */
.main {
  background-color: #f5f5f5;
  padding: 20px;
  height: calc(100vh - 100px); /* 减去头部高度 */
  overflow-y: auto;
}
</style>