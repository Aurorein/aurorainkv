<template>
  <el-menu
    unique-opened
    class="menu"
    :default-active="defaultActiveIndex"
    background-color="#3C4E60"
    text-color="#ffffff"
    active-text-color="#ffffff"
  >
    <el-sub-menu index="1">
      <template #title>
        <span>控制台</span>
      </template>
      <router-link :to="{ name: 'console-shardsys' }">
        <el-menu-item index="1-1">分片管理</el-menu-item>
      </router-link>
      <router-link :to="{ name: 'console-kv' }">
        <el-menu-item index="1-2">kv管理</el-menu-item>
      </router-link>
    </el-sub-menu>
    <el-sub-menu index="2">
      <template #title>
        <span>节点管理</span>
      </template>
      <router-link :to="{ name: 'nodes-shardmaster' }">
        <el-menu-item index="2-1">shardmaster节点</el-menu-item>
      </router-link>
      <router-link :to="{ name: 'nodes-shardkv' }">
        <el-menu-item index="2-2">shardkv节点</el-menu-item>
      </router-link>
    </el-sub-menu>
    <el-sub-menu index="3">
      <template #title>
        <span>可视化验证</span>
      </template>
      <router-link :to="{ name: 'raft-views' }">
        <el-menu-item index="3-1">Raft可视化</el-menu-item>
      </router-link>
      <router-link :to="{ name: 'consistency-views' }">
        <el-menu-item index="3-2">单client验证</el-menu-item>
      </router-link>
      <router-link :to="{ name: 'concurrent-views' }">
        <el-menu-item index="3-3">并发验证</el-menu-item>
      </router-link>
    </el-sub-menu>
  </el-menu>
</template>

<script setup>
const defaultActiveIndex = ref('1-1') // 默认激活的菜单项
</script>

<style scoped>
.menu {
  height: 100%; /* 高度占满父容器 */
  border-right: none;
}

.menu :deep(.el-sub-menu__title) {
  font-size: 16px;
}

.menu :deep(.el-menu-item) {
  font-size: 14px;
}

.menu :deep(.el-menu-item.is-active) {
  background-color: #1f2d3d !important;
}

.menu :deep(.el-menu-item:hover) {
  background-color: #1f2d3d !important;
}
</style>