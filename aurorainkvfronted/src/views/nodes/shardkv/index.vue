<template>
  <div class="min-h-screen bg-gray-100 p-8">
    <!-- 操作按钮组 -->
    <div class="flex flex-wrap justify-center gap-4 mb-8">
      <!-- 刷新按钮 -->
      <button @click="handleRefresh" :disabled="loading"
        class="flex items-center bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-3 px-6 rounded-lg shadow-md transition duration-300 disabled:opacity-50">
        <svg v-if="loading" class="animate-spin -ml-1 mr-2 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg"
          fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
          </path>
        </svg>
        <span v-if="loading">刷新中...</span>
        <span v-else>刷新节点</span>
      </button>

      <!-- 新增集群按钮 -->
      <button @click="openModal"
        class="flex items-center bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 px-6 rounded-lg shadow-md transition duration-300">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd"
            d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"
            clip-rule="evenodd" />
        </svg>
        新增集群
      </button>
    </div>

    <!-- 通知 -->
    <div v-if="showToast"
      class="fixed top-4 right-4 bg-green-500 text-white px-4 py-2 rounded shadow-lg transition-all z-50">
      {{ toastMessage }}
    </div>

    <!-- 集群列表 -->
    <div class="space-y-8">
      <div v-for="(group, gid) in clusterData" :key="gid"
        class="bg-white rounded-xl shadow-lg p-6 border-2 border-blue-200">
        <!-- 集群标题 -->
        <div class="flex flex-wrap justify-between items-center mb-6 pb-4 border-b border-gray-200 gap-2">
          <h2 class="text-2xl font-bold text-gray-800">
            <span class="text-blue-600">Group ID:</span> {{ gid }}
            <span class="text-sm font-normal ml-2 text-gray-500">(节点数: {{ group.count }})</span>
          </h2>
          <div class="flex flex-wrap gap-2">
            <span class="bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-sm font-medium">
              Max Raft State: {{ group.maxRaftState }}
            </span>
            <!-- 关闭Group按钮 -->
            <button @click.stop="shutdownGroup(gid)"
              class="flex items-center bg-red-600 hover:bg-red-700 text-white font-semibold py-1 px-3 rounded-lg shadow-md transition duration-300">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 mr-1" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd"
                  d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                  clip-rule="evenodd" />
              </svg>
              关闭集群
            </button>
          </div>
        </div>

        <!-- 节点列表 -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div v-for="(node, serverId) in group.shardKvServers" :key="serverId"
            @click="toggleNodeDetails(gid, serverId)"
            class="bg-gray-50 rounded-lg p-4 transition-all hover:shadow-md border"
            :class="{
              'border-blue-300': node.state.raft.leader,
              'border-gray-200': !node.state.raft.leader
            }">
            <!-- 节点头部信息 -->
            <div class="flex justify-between items-center mb-2">
              <div class="flex items-center">
                <span class="text-lg font-semibold text-gray-800">节点 {{ serverId }}</span>
                <span v-if="node.state.raft.leader" class="ml-2 bg-yellow-100 text-yellow-800 px-2 py-1 rounded-full text-xs font-medium">
                  Leader
                </span>
                <span v-else class="ml-2 bg-blue-100 text-blue-800 px-2 py-1 rounded-full text-xs font-medium">
                  Follower
                </span>
              </div>
              <span :class="{
                'bg-green-100 text-green-800': node.state.dead === 0,
                'bg-red-100 text-red-800': node.state.dead !== 0,
              }" class="px-3 py-1 rounded-full text-sm font-medium">
                {{ node.state.dead === 0 ? '运行中' : '已停止' }}
              </span>
            </div>

            <!-- 连接/断开按钮组 -->
            <div class="flex gap-2 mb-3">
              <button @click.stop="connectNode(gid, serverId)" 
                class="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold py-1 px-2 rounded shadow transition flex items-center justify-center">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3 mr-1" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M12.586 4.586a2 2 0 112.828 2.828l-3 3a2 2 0 01-2.828 0 1 1 0 00-1.414 1.414 4 4 0 005.656 0l3-3a4 4 0 00-5.656-5.656l-1.5 1.5a1 1 0 101.414 1.414l1.5-1.5zm-5 5a2 2 0 012.828 0 1 1 0 101.414-1.414 4 4 0 00-5.656 0l-3 3a4 4 0 105.656 5.656l1.5-1.5a1 1 0 10-1.414-1.414l-1.5 1.5a2 2 0 11-2.828-2.828l3-3z" clip-rule="evenodd" />
                </svg>
                连接
              </button>
              <button @click.stop="disconnectNode(gid, serverId)"
                class="flex-1 bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold py-1 px-2 rounded shadow transition flex items-center justify-center">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3 mr-1" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M2.707 7.293a1 1 0 010-1.414L5.586 3H11a1 1 0 011 1v4a1 1 0 01-1 1H5.586L2.707 7.293zM9 13a1 1 0 011-1h5.586l2.707 2.707a1 1 0 01-1.414 1.414L14.414 14H10a1 1 0 01-1-1V13z" clip-rule="evenodd" />
                </svg>
                断开
              </button>
              <button @click.stop="toggleNodeDetails(gid, serverId)"
                class="flex-1 bg-gray-600 hover:bg-gray-700 text-white text-xs font-semibold py-1 px-2 rounded shadow transition flex items-center justify-center">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3 mr-1" viewBox="0 0 20 20" fill="currentColor">
                  <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
                  <path fill-rule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clip-rule="evenodd" />
                </svg>
                详情
              </button>
            </div>

            <!-- Raft状态卡片 -->
            <div class="bg-white p-3 rounded-md shadow-sm mb-3">
              <div class="flex justify-between items-center mb-1">
                <span class="text-sm font-medium text-gray-700">Raft 状态</span>
                <span class="text-xs font-medium" :class="{
                  'text-purple-600': node.state.raft.leader,
                  'text-blue-600': !node.state.raft.leader
                }">
                  Term: {{ node.state.raft.term }}
                </span>
              </div>
              <div class="grid grid-cols-2 gap-2 text-xs">
                <div class="bg-gray-50 p-1 rounded">
                  <div class="text-gray-500">日志索引</div>
                  <div class="font-medium">{{ node.state.raft.lastLog.index }}</div>
                </div>
                <div class="bg-gray-50 p-1 rounded">
                  <div class="text-gray-500">应用索引</div>
                  <div class="font-medium">{{ node.state.lastApplied }}</div>
                </div>
              </div>
            </div>

            <!-- 服务信息 -->
            <div class="text-sm space-y-1">
              <p class="text-gray-700">
                <span class="font-semibold">KV服务:</span>
                {{ group.kvRaftServiceInfos[serverId].serviceHost }}:{{ group.kvRaftServiceInfos[serverId].servicePort }}
              </p>
              <p class="text-gray-700">
                <span class="font-semibold">Raft端口:</span>
                {{ group.raftServiceInfos[serverId].servicePort }}
              </p>
            </div>

            <!-- 详细信息 -->
            <div v-if="node.showDetails" class="mt-4 pt-3 border-t border-gray-200 space-y-3">
              <!-- 键值对查看区域 -->
              <div>
                <button @click.stop="fetchKeyValuePairs(gid, serverId)"
                  class="w-full bg-purple-600 hover:bg-purple-700 text-white font-semibold py-2 px-4 rounded-lg shadow-md transition duration-300 text-sm flex items-center justify-center">
                  <svg v-if="node.kvPairsLoading" class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg"
                    fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
                    </path>
                  </svg>
                  <span v-if="node.kvPairsLoading">加载中...</span>
                  <span v-else>查看键值对</span>
                </button>

                <div v-if="node.kvPairs" class="mt-2">
                  <h4 class="font-semibold text-gray-800 mb-1">键值对 (共 {{ node.kvPairs.length }} 个)</h4>
                  <div class="max-h-60 overflow-y-auto border rounded-md p-2 bg-gray-50">
                    <div v-for="(pair, index) in node.kvPairs" :key="index"
                      class="text-sm text-gray-700 py-1 border-b last:border-b-0">
                      <template v-for="(value, key) in pair" :key="key">
                        <span class="font-medium">{{ key }}:</span> {{ value }}
                      </template>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增集群模态框 -->
    <div v-if="isModalOpen" class="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50 z-50">
      <div class="bg-white rounded-xl shadow-lg p-8 w-full max-w-md">
        <h2 class="text-2xl font-semibold text-gray-800 mb-6">新增集群</h2>
        <form @submit.prevent="submitForm">
          <div class="mb-6">
            <label for="gid" class="block text-sm font-medium text-gray-700">集群 ID (gid)</label>
            <input type="number" id="gid" v-model="addClusterReq.gid" min="1"
              class="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
              placeholder="请输入集群 ID" required />
          </div>

          <div class="mb-6">
            <label for="servers" class="block text-sm font-medium text-gray-700">服务器数组</label>
            <input type="text" id="servers" v-model="addClusterReq.servers"
              class="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
              placeholder="请输入服务器数组（例如：1,2,3）" required />
          </div>

          <!-- <div class="mb-6">
            <label for="maxRaftState" class="block text-sm font-medium text-gray-700">最大 Raft 状态</label>
            <input type="number" id="maxRaftState" v-model="addClusterReq.maxRaftState" min="-1"
              class="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
              placeholder="请输入最大 Raft 状态" required />
          </div> -->

          <div class="flex justify-end space-x-4">
            <button type="button" @click="closeModal"
              class="bg-gray-300 hover:bg-gray-400 text-gray-800 font-semibold py-2 px-4 rounded-lg shadow-md transition duration-300">
              取消
            </button>
            <button type="submit" :disabled="loading"
              class="bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 px-4 rounded-lg shadow-md transition duration-300 disabled:opacity-50">
              <span v-if="loading">处理中...</span>
              <span v-else>确认</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { getKVNodes, addKVCluster, iterator, shutdown, disconnect, connect } from '@/api/nodes/shardKv';

// 响应式数据
const clusterData = reactive({});
const addClusterReq = reactive({
  gid: null,
  servers: '',
  maxRaftState: -1
});
const isModalOpen = ref(false);
const loading = ref(false);
const showToast = ref(false);
const toastMessage = ref('');

// 方法
const openModal = () => {
  isModalOpen.value = true;
};

const closeModal = () => {
  isModalOpen.value = false;
};

const toggleNodeDetails = (gid, serverId) => {
  const node = clusterData[gid].shardKvServers[serverId];
  node.showDetails = !node.showDetails;
};

const showToastMessage = (message) => {
  toastMessage.value = message;
  showToast.value = true;
  setTimeout(() => {
    showToast.value = false;
  }, 3000);
};

const handleRefresh = () => {
  loading.value = true;
  fetchClusterData();
};

const submitForm = () => {
  loading.value = true;
  addClusterReq.servers = addClusterReq.servers.split(',').map(Number);

  addKVCluster(addClusterReq)
    .then((response) => {
      if (response.status !== 200) {
        throw new Error('请求失败，状态码异常');
      }

      const responseData = response.data;
      if (!responseData) {
        throw new Error('返回数据格式错误，缺少data字段');
      }

      const addedGid = addClusterReq.gid.toString();
      const newGroup = responseData[addedGid];

      if (!newGroup) {
        throw new Error(`返回数据中找不到gid为${addedGid}的集群信息`);
      }

      // 处理节点数据
      const processedGroup = {
        ...newGroup,
        shardKvServers: Object.keys(newGroup.shardKvServers).reduce((servers, serverId) => {
          servers[serverId] = {
            ...newGroup.shardKvServers[serverId],
            showDetails: false,
            kvPairs: null,
            kvPairsLoading: false
          };
          return servers;
        }, {})
      };

      clusterData[addedGid] = processedGroup;

      // 重置表单
      Object.assign(addClusterReq, {
        gid: null,
        servers: '',
        maxRaftState: -1
      });
      closeModal();
      showToastMessage(`集群 ${addedGid} 添加成功`);
    })
    .catch((error) => {
      console.error('新增集群失败:', error);
      showToastMessage('新增集群失败: ' + error.message);
    })
    .finally(() => {
      loading.value = false;
    });
};

const shutdownServers = () => {
  shutdown()
    .then(() => {
      fetchClusterData();
      showToastMessage('服务器已关闭');
    })
    .catch((error) => {
      showToastMessage('关闭服务器失败: ' + error.message);
    });
};

const fetchKeyValuePairs = (gid, serverId) => {
  const node = clusterData[gid].shardKvServers[serverId];
  node.kvPairsLoading = true;

  iterator({ gid: parseInt(gid), sid: parseInt(serverId) })
    .then(response => {
      if (response.status === 200 && response.data) {
        node.kvPairs = response.data;
      } else {
        node.kvPairs = [];
        showToastMessage('获取键值对失败: 数据格式错误');
      }
    })
    .catch(error => {
      console.error('获取键值对失败:', error);
      showToastMessage('获取键值对失败: ' + error.message);
      node.kvPairs = [];
    })
    .finally(() => {
      node.kvPairsLoading = false;
    });
};

const connectNode = (gid, serverId) => {
  const req = {
    gid: parseInt(gid),
    id: parseInt(serverId)
  };
  
  connect(req)
    .then(() => {
      showToastMessage(`节点 ${gid}-${serverId} 连接成功`);
      // 可选: 刷新数据以反映最新状态
      fetchClusterData();
    })
    .catch(error => {
      console.error('连接节点失败:', error);
      showToastMessage(`连接节点 ${gid}-${serverId} 失败: ${error.message}`);
    });
};

const disconnectNode = (gid, serverId) => {
  const req = {
    gid: parseInt(gid),
    id: parseInt(serverId)
  };
  
  disconnect(req)
    .then(() => {
      showToastMessage(`节点 ${gid}-${serverId} 断开成功`);
      // 可选: 刷新数据以反映最新状态
      fetchClusterData();
    })
    .catch(error => {
      console.error('断开节点失败:', error);
      showToastMessage(`断开节点 ${gid}-${serverId} 失败: ${error.message}`);
    });
};

const fetchClusterData = () => {
  loading.value = true;
  getKVNodes()
    .then((response) => {
      if (response.status !== 200) {
        throw new Error('请求失败，状态码异常');
      }

      const responseData = response.data;
      if (!responseData) {
        throw new Error('返回数据格式错误，缺少data字段');
      }

      // 清空现有数据
      Object.keys(clusterData).forEach(key => {
        delete clusterData[key];
      });

      // 添加新数据
      Object.entries(responseData).forEach(([gid, group]) => {
        const processedGroup = {
          ...group,
          shardKvServers: Object.keys(group.shardKvServers).reduce((servers, serverId) => {
            servers[serverId] = {
              ...group.shardKvServers[serverId],
              showDetails: false,
              kvPairs: null,
              kvPairsLoading: false
            };
            return servers;
          }, {})
        };
        clusterData[gid] = processedGroup;
      });

      showToastMessage('节点数据刷新成功');
    })
    .catch((error) => {
      console.error('获取集群数据失败:', error);
      showToastMessage('获取集群数据失败: ' + error.message);
    })
    .finally(() => {
      loading.value = false;
    });
};

const shutdownGroup = (gid) => {
  if(confirm(`确定要关闭集群 ${gid} 吗？此操作不可撤销！`)) {
    loading.value = true;
    shutdown(parseInt(gid))
      .then(() => {
        // 从clusterData中移除该group
        delete clusterData[gid];
        showToastMessage(`集群 ${gid} 已成功关闭`);
      })
      .catch(error => {
        console.error('关闭集群失败:', error);
        showToastMessage(`关闭集群 ${gid} 失败: ${error.message}`);
      })
      .finally(() => {
        loading.value = false;
      });
  }
};
// 生命周期钩子
onMounted(() => {
  fetchClusterData();
});
</script>

<style scoped>
/* 基础样式 */
.min-h-screen {
  min-height: 100vh;
}

.bg-gray-100 {
  background-color: #f3f4f6;
}

.bg-white {
  background-color: #ffffff;
}

.bg-gray-50 {
  background-color: #f9fafb;
}

.text-gray-800 {
  color: #1f2937;
}

.text-gray-700 {
  color: #374151;
}

.text-gray-600 {
  color: #4b5563;
}

.text-blue-600 {
  color: #2563eb;
}

.border-blue-200 {
  border-color: #bfdbfe;
}

.font-semibold {
  font-weight: 600;
}

.font-bold {
  font-weight: 700;
}

.text-sm {
  font-size: 0.875rem;
}

.text-2xl {
  font-size: 1.5rem;
}

.rounded-xl {
  border-radius: 1rem;
}

.rounded-lg {
  border-radius: 0.5rem;
}

.shadow-lg {
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}

.grid {
  display: grid;
}

.grid-cols-1 {
  grid-template-columns: repeat(1, minmax(0, 1fr));
}

.md\:grid-cols-2 {
  @media (min-width: 768px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.lg\:grid-cols-3 {
  @media (min-width: 1024px) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.gap-4 {
  gap: 1rem;
}

.space-y-8>*+* {
  margin-top: 2rem;
}

.cursor-pointer {
  cursor: pointer;
}

.transition-all {
  transition-property: all;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
  transition-duration: 150ms;
}

.hover\:shadow-md:hover {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

/* 按钮样式 */
.bg-indigo-600 {
  background-color: #4f46e5;
}

.hover\:bg-indigo-700:hover {
  background-color: #4338ca;
}

.bg-blue-600 {
  background-color: #2563eb;
}

.hover\:bg-blue-700:hover {
  background-color: #1d4ed8;
}

.bg-red-600 {
  background-color: #dc2626;
}

.hover\:bg-red-700:hover {
  background-color: #b91c1c;
}

.bg-purple-600 {
  background-color: #7c3aed;
}

.hover\:bg-purple-700:hover {
  background-color: #6d28d9;
}

/* 状态标签样式 */
.bg-yellow-100 {
  background-color: #fef3c7;
}

.text-yellow-800 {
  color: #92400e;
}

.bg-blue-100 {
  background-color: #dbeafe;
}

.text-blue-800 {
  color: #1e40af;
}

.bg-green-100 {
  background-color: #d1fae5;
}

.text-green-800 {
  color: #065f46;
}

.bg-red-100 {
  background-color: #fee2e2;
}

.text-red-800 {
  color: #991b1b;
}

/* 动画 */
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.animate-spin {
  animation: spin 1s linear infinite;
}

/* 通知样式 */
.toast {
  position: fixed;
  top: 1rem;
  right: 1rem;
  background-color: #10b981;
  color: white;
  padding: 0.75rem 1.5rem;
  border-radius: 0.375rem;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  z-index: 50;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

/* 滚动条样式 */
.max-h-60 {
  max-height: 15rem;
}

.overflow-y-auto {
  overflow-y: auto;
}

/* 自定义滚动条 */
.overflow-y-auto::-webkit-scrollbar {
  width: 6px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 响应式调整 */
@media (max-width: 640px) {
  .flex-wrap {
    flex-wrap: wrap;
  }
  
  .gap-4 {
    gap: 0.5rem;
  }
  
  .p-8 {
    padding: 1rem;
  }
  
  .p-6 {
    padding: 1rem;
  }
}

.bg-emerald-600 {
  background-color: #059669;
}
.hover\:bg-emerald-700:hover {
  background-color: #047857;
}

.bg-rose-600 {
  background-color: #e11d48;
}
.hover\:bg-rose-700:hover {
  background-color: #be123c;
}
/* 新增关闭按钮样式 */
.bg-red-600 {
  background-color: #dc2626;
}
.hover\:bg-red-700:hover {
  background-color: #b91c1c;
}
</style>