<template>
  <div class="min-h-screen bg-gray-100 p-8">
    <!-- 操作按钮组 -->
    <div class="flex justify-center space-x-4 mb-8">
      <!-- 刷新按钮 -->
      <button @click="refreshNodes"
        class="bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-3 px-6 rounded-lg shadow-md transition duration-300 flex items-center">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd"
            d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z"
            clip-rule="evenodd" />
        </svg>
        刷新节点
      </button>

      <!-- 新增集群按钮 -->
      <button @click="openModal"
        class="bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 px-6 rounded-lg shadow-md transition duration-300 flex items-center">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd"
            d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"
            clip-rule="evenodd" />
        </svg>
        新增集群
      </button>

      <!-- 更新ShardClient按钮 -->
      <button @click="updateShardClient" :disabled="!clusterCount"
        class="bg-green-600 hover:bg-green-700 text-white font-semibold py-3 px-6 rounded-lg shadow-md transition duration-300 flex items-center disabled:opacity-50 disabled:cursor-not-allowed">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd"
            d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z"
            clip-rule="evenodd" />
        </svg>
        更新ShardClient ({{ clusterCount }})
      </button>

      <!-- 关闭服务器按钮 -->
      <button @click="shutdownServers"
        class="bg-red-600 hover:bg-red-700 text-white font-semibold py-3 px-6 rounded-lg shadow-md transition duration-300 flex items-center">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd"
            d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z"
            clip-rule="evenodd" />
        </svg>
        关闭服务器
      </button>
    </div>

    <!-- 美化通知 -->
    <div v-if="showToast" class="toast">
      {{ toastMessage }}
    </div>

    <!-- 节点列表 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div v-for="(node, index) in nodes" :key="index" 
        class="bg-white rounded-xl shadow-lg p-6 cursor-pointer transition-transform transform hover:scale-105">
        <!-- 简略信息 -->
        <div class="flex justify-between items-center">
          <div>
            <span class="text-lg font-semibold text-gray-800">节点 ID: {{ node.me }}</span>
            <span :class="getRaftStateClass(node)" class="ml-2 px-2 py-1 rounded-full text-xs font-medium">
              {{ getRaftStateText(node) }}
            </span>
          </div>
          <span :class="{
            'bg-green-100 text-green-800': node.dead === 0,
            'bg-red-100 text-red-800': node.dead !== 0,
          }" class="px-3 py-1 rounded-full text-sm font-medium">
            {{ node.dead === 0 ? '运行中' : '已停止' }}
          </span>
        </div>
        
        <div class="mt-4 flex justify-between items-center">
          <div>
            <p class="text-gray-700 font-medium"><span class="font-semibold">IP:</span> {{
              node.raftServiceInfo.serviceHost }}</p>
            <p class="text-gray-700 font-medium"><span class="font-semibold">端口:</span> {{
              node.raftServiceInfo.servicePort }}</p>
          </div>
          <span v-if="node.raft.leader" class="bg-yellow-100 text-yellow-800 px-2 py-1 rounded-full text-xs font-medium">
            主节点
          </span>
          <span v-else class="bg-blue-100 text-blue-800 px-2 py-1 rounded-full text-xs font-medium">
            从节点
          </span>
        </div>

        <!-- Raft状态概览 -->
        <div class="mt-4 grid grid-cols-2 gap-2">
          <div class="bg-gray-50 p-2 rounded">
            <p class="text-xs text-gray-500">当前任期</p>
            <p class="font-semibold">{{ node.raft.term }}</p>
          </div>
          <div class="bg-gray-50 p-2 rounded">
            <p class="text-xs text-gray-500">最后日志索引</p>
            <p class="font-semibold">{{ node.raft.lastLog.index }}</p>
          </div>
          <div class="bg-gray-50 p-2 rounded">
            <p class="text-xs text-gray-500">最后日志任期</p>
            <p class="font-semibold">{{ node.raft.lastLog.term }}</p>
          </div>
          <div class="bg-gray-50 p-2 rounded">
            <p class="text-xs text-gray-500">持久化大小</p>
            <p class="font-semibold">{{ node.raft.raftPersistSize }}B</p>
          </div>
        </div>

        <!-- 连接/断开连接按钮 -->
        <!-- <div class="mt-4 flex space-x-2">
          <button @click.stop="connectNode(node.me)" 
            class="flex-1 bg-green-500 hover:bg-green-600 text-white font-semibold py-2 px-4 rounded-lg shadow-md transition duration-300 text-sm">
            连接节点
          </button>
          <button @click.stop="disconnectNode(node.me)"
            class="flex-1 bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2 px-4 rounded-lg shadow-md transition duration-300 text-sm">
            断开连接
          </button>
        </div> -->

        <!-- 详细信息 -->
        <div v-if="node.showDetails" class="mt-6 space-y-4">
          <h3 class="text-xl font-bold text-gray-800">详细信息</h3>
          <div class="space-y-2">
            <p class="text-gray-700 font-medium"><span class="font-semibold">最后应用的索引:</span> {{ node.lastApplied }}</p>
            <p class="text-gray-700 font-medium"><span class="font-semibold">配置数量:</span> {{ node.configs.length }}</p>
            <div v-for="(config, configIndex) in node.configs" :key="configIndex" class="bg-gray-50 p-4 rounded-lg">
              <h4 class="text-lg font-semibold text-gray-800">配置 {{ configIndex + 1 }}</h4>
              <pre class="text-sm text-gray-600 mt-2 overflow-x-auto">{{ JSON.stringify(config, null, 2) }}</pre>
            </div>
          </div>
          <div class="mt-4">
            <h4 class="text-lg font-semibold text-gray-800">Raft 服务信息</h4>
            <pre
              class="text-sm text-gray-600 mt-2 overflow-x-auto">{{ JSON.stringify(node.raftServiceInfo, null, 2) }}</pre>
          </div>
          <div class="mt-4">
            <h4 class="text-lg font-semibold text-gray-800">KV Raft 服务信息</h4>
            <pre
              class="text-sm text-gray-600 mt-2 overflow-x-auto">{{ JSON.stringify(node.kvRaftServiceInfo, null, 2) }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- 模态框 -->
    <div v-if="isModalOpen" class="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50">
      <div class="bg-white rounded-xl shadow-lg p-8 w-96">
        <h2 class="text-2xl font-semibold text-gray-800 mb-6">新增集群</h2>
        <form @submit.prevent="submitForm">
          <div class="mb-6">
            <label for="nodeCount" class="block text-sm font-medium text-gray-700">节点数</label>
            <input type="number" id="nodeCount" v-model="nodeCount" min="1" max="10"
              class="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
              placeholder="请输入节点数" required />
          </div>
          <div class="flex justify-end space-x-4">
            <button type="button" @click="closeModal"
              class="bg-gray-300 hover:bg-gray-400 text-gray-800 font-semibold py-2 px-4 rounded-lg shadow-md transition duration-300">
              取消
            </button>
            <button type="submit"
              class="bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 px-4 rounded-lg shadow-md transition duration-300">
              确认
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script>
import { getNodes, addCluster, shutdown, connect, disconnect } from '@/api/nodes/shardMaster';
import { setShardClient } from '@/api/nodes/shardkv';

export default {
  name: 'ShardMaster',
  data() {
    return {
      nodes: [],
      isModalOpen: false,
      nodeCount: 3,
      clusterCount: 0,
      showToast: false,
      toastMessage: '',
    };
  },
  mounted() {
    this.getNodes();
  },
  methods: {
    /** 获取节点列表 */
    getNodes() {
      getNodes()
        .then((response) => {
          this.nodes = []; // 清空现有节点
          const newCluster = response.data;
          this.clusterCount = newCluster.count;

          newCluster.shardMasterServers.forEach((server, index) => {
            this.nodes.push({
              ...server.state,
              raftServiceInfo: newCluster.raftServiceInfos[index],
              kvRaftServiceInfo: newCluster.kvRaftServiceInfos[index],
              showDetails: false,
            });
          });
          this.showToastMessage('节点数据已刷新');
        })
        .catch((error) => {
          this.showToastMessage('获取节点失败:' + error);
          console.error('获取节点失败:', error);
        });
    },

    /** 刷新节点 */
    refreshNodes() {
      this.getNodes();
    },

    /** 获取Raft状态文本 */
    getRaftStateText(node) {
      if (node.dead !== 0) return '已停止';
      if (node.raft.leader) return 'Leader';
      return 'Follower';
    },

    /** 获取Raft状态样式类 */
    getRaftStateClass(node) {
      if (node.dead !== 0) return 'bg-gray-100 text-gray-800';
      if (node.raft.leader) return 'bg-purple-100 text-purple-800';
      return 'bg-blue-100 text-blue-800';
    },

    /** 连接节点 */
    connectNode(nodeId) {
      connect(nodeId)
        .then(() => {
          this.showToastMessage(`节点 ${nodeId} 连接成功`);
          this.refreshNodes();
        })
        .catch((error) => {
          this.showToastMessage(`节点 ${nodeId} 连接失败: ${error.message}`);
          console.error('连接节点失败:', error);
        });
    },

    /** 断开节点连接 */
    disconnectNode(nodeId) {
      disconnect(nodeId)
        .then(() => {
          this.showToastMessage(`节点 ${nodeId} 断开连接成功`);
          this.refreshNodes();
        })
        .catch((error) => {
          this.showToastMessage(`节点 ${nodeId} 断开连接失败: ${error.message}`);
          console.error('断开连接失败:', error);
        });
    },

    openModal() {
      this.isModalOpen = true;
    },

    closeModal() {
      this.isModalOpen = false;
    },

    shutdownServers() {
      shutdown()
        .then(() => {
          this.getNodes();
          this.showToastMessage('服务器已关闭');
        })
        .catch((error) => {
          this.showToastMessage('关闭节点失败:' + error);
        });
    },

    showToastMessage(message) {
      this.toastMessage = message;
      this.showToast = true;
      setTimeout(() => {
        this.showToast = false;
      }, 3000);
    },

    updateShardClient() {
      if (!this.clusterCount) return;

      setShardClient(this.clusterCount)
        .then((response) => {
          this.showToastMessage('ShardClient更新成功');
          console.log('ShardClient更新成功:', response);
        })
        .catch((error) => {
          this.showToastMessage('ShardClient更新失败:' + error);
          console.error('ShardClient更新失败:', error);
        });
    },

    submitForm() {
      addCluster(this.nodeCount)
        .then((response) => {
          const newCluster = response.data;
          this.clusterCount = newCluster.count;

          newCluster.shardMasterServers.forEach((server, index) => {
            this.nodes.push({
              ...server.state,
              raftServiceInfo: newCluster.raftServiceInfos[index],
              kvRaftServiceInfo: newCluster.kvRaftServiceInfos[index],
              showDetails: false,
            });
          });
          this.updateShardClient();
          this.showToastMessage(`成功新增${this.nodeCount}个节点集群`);
          this.closeModal();
        })
        .catch((error) => {
          this.showToastMessage('新增集群失败:' + error);
          console.error('新增集群失败:', error);
        });
    },

    toggleNodeDetails(index) {
      this.nodes[index].showDetails = !this.nodes[index].showDetails;
    },
  },
};
</script>

<style scoped>
.toast {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  background-color: #4caf50;
  color: white;
  padding: 10px 20px;
  border-radius: 5px;
  font-size: 16px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  opacity: 0;
  animation: fadeInOut 3s forwards;
}

@keyframes fadeInOut {
  0% {
    opacity: 0;
    transform: translateX(-50%) translateY(-10px);
  }
  10% {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
  90% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translateX(-50%) translateY(-10px);
  }
}

button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

button {
  transition: all 0.2s ease-in-out;
}

button:disabled {
  transform: none;
  box-shadow: none;
}

.grid {
  gap: 20px;
}

/* 主节点高亮样式 */
.bg-yellow-100 {
  background-color: #fef9c3;
}
.text-yellow-800 {
  color: #92400e;
}

/* 从节点样式 */
.bg-blue-100 {
  background-color: #dbeafe;
}
.text-blue-800 {
  color: #1e40af;
}

/* Leader状态样式 */
.bg-purple-100 {
  background-color: #f3e8ff;
}
.text-purple-800 {
  color: #6b21a8;
}

/* 停止状态样式 */
.bg-gray-100 {
  background-color: #f3f4f6;
}
.text-gray-800 {
  color: #1f2937;
}

/* 连接按钮样式 */
.bg-green-500 {
  background-color: #10b981;
}
.hover\:bg-green-600:hover {
  background-color: #059669;
}

/* 断开连接按钮样式 */
.bg-orange-500 {
  background-color: #f97316;
}
.hover\:bg-orange-600:hover {
  background-color: #ea580c;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .flex.justify-center.space-x-4 {
    flex-wrap: wrap;
  }
  .flex.justify-center.space-x-4 button {
    margin-bottom: 8px;
  }
}
</style>