<template>
  <div class="min-h-screen bg-gray-50 p-6">
    <!-- 标题区域 -->
    <div class="max-w-7xl mx-auto">
      <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-gray-800 mb-2">Shard 分片管理</h1>
        <!-- <p class="text-gray-600">实时监控和管理分布式系统的分片配置</p> -->
      </div>
      <!-- 操作按钮组 -->
      <div class="bg-white rounded-xl shadow-lg p-6 mb-6">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <!-- 查询配置按钮 -->
          <button @click="showQueryModal = true"
            class="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded transition duration-300 flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd"
                d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z"
                clip-rule="evenodd" />
            </svg>
            查询配置
          </button>
          <!-- 加入组按钮 -->
          <button @click="showJoinModal = true"
            class="bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded transition duration-300 flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd"
                d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"
                clip-rule="evenodd" />
            </svg>
            加入Group
          </button>
          <!-- 离开组按钮 -->
          <button @click="showLeaveModal = true"
            class="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded transition duration-300 flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M5 10a1 1 0 011-1h8a1 1 0 110 2H6a1 1 0 01-1-1z" clip-rule="evenodd" />
            </svg>
            删除Group
          </button>
          <!-- 移动分片按钮 -->
          <button @click="showMoveModal = true"
            class="bg-purple-500 hover:bg-purple-600 text-white px-4 py-2 rounded transition duration-300 flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
              <path d="M8 9a3 3 0 100-6 3 3 0 000 6zM8 11a6 6 0 016 6H2a6 6 0 016-6z" />
            </svg>
            移动分片
          </button>
        </div>
      </div>
      <!-- 配置信息展示 和 矩阵视图 -->
      <div class="flex flex-col md:flex-row gap-6">
        <!-- 环形分布图 -->
        <div class="bg-white rounded-xl shadow-lg p-6 flex-1">
          <div class="flex justify-between items-center mb-6">
            <h2 class="text-xl font-semibold text-gray-800">
              当前配置版本: <span class="text-blue-600">{{ config.num }}</span>
            </h2>
            <div class="flex space-x-2">
              <span class="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm">
                分片总数: {{ shards.length }}
              </span>
              <span class="px-3 py-1 bg-green-100 text-green-800 rounded-full text-sm">
                组数量: {{ Object.keys(groups).length }}
              </span>
            </div>
          </div>
          <!-- 环形分布图 -->
          <div class="relative w-64 h-64 mx-auto">
            <svg viewBox="0 0 100 100" class="w-full h-full">
              <!-- 背景圆环 -->
              <circle cx="50" cy="50" r="40" fill="none" stroke="#e5e7eb" stroke-width="10" />
              <!-- 分片分配 - 按组分色 -->
              <g v-for="(gid, index) in shards" :key="index">
                <path :d="getArcPath(index, shards.length, 40, 35)" :fill="getGroupColor(gid)" stroke="white"
                  stroke-width="0.5" class="transition-all duration-200"
                  :class="{ 'opacity-90': highlightedGroup === null || highlightedGroup === gid, 'opacity-30': highlightedGroup !== null && highlightedGroup !== gid }" />
                <text :x="50 + 37 * Math.cos(getMiddleAngle(index, shards.length))"
                  :y="50 + 37 * Math.sin(getMiddleAngle(index, shards.length))" text-anchor="middle"
                  dominant-baseline="middle" :fill="getTextColorForBackground(getGroupColor(gid))" font-size="5"
                  font-weight="bold" class="pointer-events-none" style="text-shadow: 0 0 2px rgba(0,0,0,0.5);">
                  {{ index }}
                </text>
              </g>
              <!-- 中心区域 -->
              <circle cx="50" cy="50" r="15" fill="white" stroke="#e5e7eb" stroke-width="1" />
            </svg>
          </div>
          <!-- 图例 -->
          <div class="flex flex-wrap justify-center mt-4 gap-3">
            <div v-for="(servers, gid) in groups" :key="gid"
              class="flex items-center px-3 py-1 bg-white rounded-full shadow-xs cursor-pointer hover:bg-gray-50 transition-colors"
              @mouseenter="highlightedGroup = parseInt(gid)" @mouseleave="highlightedGroup = null">
              <div class="w-3 h-3 rounded-full mr-2" :style="{ backgroundColor: getGroupColor(gid) }"></div>
              <span class="text-sm" :class="getGroupTextColor(gid)">组 {{ gid }} ({{ getShardCount(gid) }})</span>
            </div>
          </div>
        </div>

        <!-- 矩阵视图 -->
        <div class="bg-white p-6 rounded-xl shadow-lg flex-1">
          <h3 class="text-lg font-medium text-gray-700 mb-4 text-center">分片矩阵视图</h3>
          <div class="grid grid-cols-5 gap-3">
            <div v-for="(gid, index) in shards" :key="index"
              :class="[getShardColor(gid), selectedShard === index ? 'ring-2 ring-blue-500 scale-105' : '']"
              class="h-14 rounded-lg flex flex-col items-center justify-center text-white font-medium shadow-md cursor-pointer transition-all duration-200 hover:shadow-lg overflow-hidden">
              <span class="text-xs text-center">分片</span>
              <span class="text-sm font-bold">{{ index }}</span>
              <span class="text-xs mt-1 text-center">组 {{ gid }}</span>
            </div>
          </div>
        </div>
      </div>
      <!-- 分组负载情况 -->
      <div class="bg-white p-6 rounded-xl shadow-lg mb-6">
        <h3 class="text-lg font-medium text-gray-700 mb-4 text-center">分组负载均衡</h3>
        <div class="space-y-4 max-w-2xl mx-auto">
          <div v-for="(servers, gid) in groups" :key="gid" class="flex items-center">
            <div class="w-16 text-sm font-medium" :class="getGroupTextColor(gid)">组 {{ gid }}</div>
            <div class="flex-1 bg-gray-200 rounded-full h-6 overflow-hidden shadow-inner">
              <div :style="{ width: `${(getShardCount(gid) / 10) * 100}%` }"
                class="h-full rounded-full flex items-center justify-end pr-2 text-xs font-medium text-white transition-all duration-500"
                :class="getShardColor(gid)">
                {{ getShardCount(gid) }}个分片
              </div>
            </div>
            <div class="w-16 text-right text-sm text-gray-500 ml-2">
              {{ Math.round((getShardCount(gid) / 10) * 100) }}%
            </div>
          </div>
        </div>
      </div>
      <!-- 组详细信息 -->
      <div class="bg-white p-6 rounded-xl shadow-lg">
        <h3 class="text-lg font-medium text-gray-700 mb-4">组详细信息</h3>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div v-for="(servers, gid) in groups" :key="gid"
            class="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow"
            :class="{ 'ring-2 ring-blue-500 bg-blue-50': selectedShard !== null && shards[selectedShard] === parseInt(gid) }">
            <div class="flex justify-between items-center mb-2">
              <h4 class="font-bold text-lg flex items-center">
                <span class="w-3 h-3 rounded-full mr-2" :class="getShardColor(gid)"></span>
                <span :class="getGroupTextColor(gid)">组 {{ gid }}</span>
              </h4>
              <span class="px-2 py-1 bg-gray-100 text-gray-800 rounded-full text-xs">
                {{ getShardCount(gid) }} 个分片
              </span>
            </div>
            <div class="space-y-2 mt-3">
              <div class="text-sm text-gray-500 mb-1">包含服务器:</div>
              <div v-for="(server, idx) in servers" :key="idx"
                class="text-sm text-gray-700 bg-white px-3 py-2 rounded border border-gray-200 hover:bg-gray-50 transition-colors">
                <span class="font-medium">服务器 #{{ server }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <!-- 操作结果提示 -->
    <transition name="fade">
      <div v-if="message"
        class="fixed bottom-4 right-4 px-6 py-3 rounded-lg shadow-lg text-white font-medium flex items-center"
        :class="messageType === 'success' ? 'bg-green-500' : 'bg-red-500'">
        <svg v-if="messageType === 'success'" xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2"
          viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd"
            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
            clip-rule="evenodd" />
        </svg>
        <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-2" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd"
            d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
            clip-rule="evenodd" />
        </svg>
        {{ message }}
      </div>
    </transition>
    <!-- 查询配置模态框 -->
    <div v-if="showQueryModal"
      class="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50 z-50 p-4 overflow-y-auto">
      <div class="bg-white rounded-xl shadow-2xl w-full max-w-sm max-h-[90vh] overflow-y-auto">
        <div class="p-6">
          <h2 class="text-2xl font-bold text-gray-800 mb-4">查询配置</h2>
          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-600 mb-1">配置版本号</label>
              <input v-model.number="queryParams.num" type="number"
                class="w-14.5/16 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent transition-all"
                placeholder="输入配置版本号 (-1表示最新配置)" />
              <p class="text-xs text-gray-500 mt-1">输入-1可查询最新配置</p>
            </div>
          </div>
        </div>
        <div class="bg-gray-50 px-6 py-4 rounded-b-xl flex justify-end space-x-3">
          <button @click="showQueryModal = false"
            class="px-5 py-2.5 bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 transition-colors font-medium">
            取消
          </button>
          <button @click="handleQuery"
            class="px-5 py-2.5 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors font-medium shadow-md">
            查询
          </button>
        </div>
      </div>
    </div>

    <!-- 加入组模态框 -->
    <div v-if="showJoinModal"
      class="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50 z-50 p-4 overflow-y-auto">
      <div class="bg-white rounded-xl shadow-2xl w-full max-w-sm max-h-[90vh] overflow-y-auto">
        <div class="p-6">
          <h2 class="text-2xl font-bold text-gray-800 mb-4">加入组</h2>
          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-600 mb-1">组ID</label>
              <input v-model.number="joinParams.gid" type="number"
                class="w-14.5/16 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-green-400 focus:border-transparent transition-all"
                placeholder="输入组ID" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-600 mb-1">服务器列表</label>
              <input v-model="joinParams.servers"
                class="w-14.5/16 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-green-400 focus:border-transparent transition-all"
                placeholder="用逗号分隔，如: 1,2,3" />
              <p class="text-xs text-gray-500 mt-1">请输入该组包含的服务器ID</p>
            </div>
          </div>
        </div>
        <div class="bg-gray-50 px-6 py-4 rounded-b-xl flex justify-end space-x-3">
          <button @click="showJoinModal = false"
            class="px-5 py-2.5 bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 transition-colors font-medium">
            取消
          </button>
          <button @click="handleJoin"
            class="px-5 py-2.5 bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors font-medium shadow-md">
            确认加入
          </button>
        </div>
      </div>
    </div>

    <!-- 离开组模态框 -->
    <div v-if="showLeaveModal"
      class="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50 z-50 p-4 overflow-y-auto">
      <div class="bg-white rounded-xl shadow-2xl w-full max-w-sm max-h-[90vh] overflow-y-auto">
        <div class="p-6">
          <h2 class="text-2xl font-bold text-gray-800 mb-4">删除组</h2>
          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-600 mb-1">组ID列表</label>
              <input v-model="leaveParams.gids"
                class="w-14.5/16 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-400 focus:border-transparent transition-all"
                placeholder="用逗号分隔，如: 1,2,3" />
              <p class="text-xs text-gray-500 mt-1">请输入要移除的组ID</p>
            </div>
          </div>
        </div>
        <div class="bg-gray-50 px-6 py-4 rounded-b-xl flex justify-end space-x-3">
          <button @click="showLeaveModal = false"
            class="px-5 py-2.5 bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 transition-colors font-medium">
            取消
          </button>
          <button @click="handleLeave"
            class="px-5 py-2.5 bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors font-medium shadow-md">
            确认离开
          </button>
        </div>
      </div>
    </div>

    <!-- 移动分片模态框 -->
    <div v-if="showMoveModal"
      class="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50 z-50 p-4 overflow-y-auto">
      <div class="bg-white rounded-xl shadow-2xl w-full max-w-sm max-h-[90vh] overflow-y-auto">
        <div class="p-6">
          <h2 class="text-2xl font-bold text-gray-800 mb-4">移动分片</h2>
          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-600 mb-1">分片ID</label>
              <input v-model.number="moveParams.shard" type="number"
                class="w-14.5/16 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-400 focus:border-transparent transition-all"
                placeholder="0-9之间的数字" min="0" max="9" />
              <p class="text-xs text-gray-500 mt-1">请输入要移动的分片ID (0-9)</p>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-600 mb-1">目标组ID</label>
              <input v-model.number="moveParams.gid" type="number"
                class="w-14.5/16 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-400 focus:border-transparent transition-all"
                placeholder="输入目标组ID" />
              <p class="text-xs text-gray-500 mt-1">分片将移动到此组</p>
            </div>
          </div>
        </div>
        <div class="bg-gray-50 px-6 py-4 rounded-b-xl flex justify-end space-x-3">
          <button @click="showMoveModal = false"
            class="px-5 py-2.5 bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 transition-colors font-medium">
            取消
          </button>
          <button @click="handleMove"
            class="px-5 py-2.5 bg-purple-500 text-white rounded-lg hover:bg-purple-600 transition-colors font-medium shadow-md">
            确认移动
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { query, join, leave, move } from '@/api/system/shardClient';

export default {
  name: 'ShardClient',
  data() {
    return {
      highlightedGroup: null,
      config: {
        num: 0,
        shards: [],
        groups: {},
      },
      selectedShard: null,
      message: '',
      messageType: 'success',
      // 模态框状态
      showQueryModal: false,
      showJoinModal: false,
      showLeaveModal: false,
      showMoveModal: false,
      // 查询参数
      queryParams: {
        num: -1
      },
      // 加入组参数
      joinParams: {
        gid: null,
        servers: ''
      },
      // 离开组参数
      leaveParams: {
        gids: ''
      },
      // 移动分片参数
      moveParams: {
        shard: null,
        gid: null
      }
    };
  },
  computed: {
    shards() {
      return this.config.shards || [];
    },
    groups() {
      return this.config.groups || {};
    },
  },
  methods: {
    // 计算中间角度
    getMiddleAngle(index, total) {
      const startAngle = (index / total) * 2 * Math.PI - Math.PI / 2;
      const endAngle = ((index + 1) / total) * 2 * Math.PI - Math.PI / 2;
      return (startAngle + endAngle) / 2;
    },
    // 计算扇形路径
    getArcPath(index, total, outerRadius, innerRadius = 0) {
      const centerX = 50;
      const centerY = 50;
      const startAngle = (index / total) * 2 * Math.PI - Math.PI / 2;
      const endAngle = ((index + 1) / total) * 2 * Math.PI - Math.PI / 2;
      const x1 = centerX + outerRadius * Math.cos(startAngle);
      const y1 = centerY + outerRadius * Math.sin(startAngle);
      const x2 = centerX + outerRadius * Math.cos(endAngle);
      const y2 = centerY + outerRadius * Math.sin(endAngle);
      const x3 = centerX + innerRadius * Math.cos(endAngle);
      const y3 = centerY + innerRadius * Math.sin(endAngle);
      const x4 = centerX + innerRadius * Math.cos(startAngle);
      const y4 = centerY + innerRadius * Math.sin(startAngle);
      const largeArcFlag = endAngle - startAngle <= Math.PI ? 0 : 1;
      return `
        M ${x1} ${y1}
        A ${outerRadius} ${outerRadius} 0 ${largeArcFlag} 1 ${x2} ${y2}
        L ${x3} ${y3}
        A ${innerRadius} ${innerRadius} 0 ${largeArcFlag} 0 ${x4} ${y4}
        Z
      `;
    },
    // 获取组的颜色
    getGroupColor(gid) {
      const colorMap = {
        0: '#3b82f6', // blue-500
        1: '#10b981', // emerald-500
        2: '#f59e0b', // amber-500
        3: '#ef4444', // red-500
        4: '#8b5cf6', // violet-500
        5: '#ec4899', // pink-500
        6: '#6366f1', // indigo-500
        7: '#14b8a6', // teal-500
        8: '#f97316', // orange-500
        9: '#64748b'  // slate-500
      };
      return colorMap[gid % 10] || '#64748b';
    },
    // 根据背景色确定文字颜色
    getTextColorForBackground(backgroundColor) {
      // 将十六进制颜色转换为RGB
      const hexToRgb = hex => {
        const bigint = parseInt(hex.replace('#', ''), 16);
        return {
          r: (bigint >> 16) & 255,
          g: (bigint >> 8) & 255,
          b: bigint & 255
        };
      };
      // 计算亮度
      const rgb = hexToRgb(backgroundColor);
      const brightness = (rgb.r * 299 + rgb.g * 587 + rgb.b * 114) / 1000;
      return brightness > 128 ? 'black' : 'white';
    },
    // 获取组文本颜色
    getGroupTextColor(gid) {
      const colorMap = {
        0: 'text-blue-700',
        1: 'text-green-700',
        2: 'text-amber-700',
        3: 'text-red-700',
        4: 'text-purple-700',
        5: 'text-pink-700',
        6: 'text-indigo-700',
        7: 'text-teal-700',
        8: 'text-orange-700',
        9: 'text-gray-700',
      };
      return colorMap[gid % 10] || 'text-gray-700';
    },
    // 获取分片颜色
    getShardColor(gid) {
      const colors = [
        'bg-blue-500', 'bg-green-500', 'bg-yellow-500',
        'bg-red-500', 'bg-purple-500', 'bg-pink-500',
        'bg-indigo-500', 'bg-teal-500', 'bg-orange-500',
        'bg-gray-500'
      ];
      return colors[gid % colors.length] || 'bg-gray-500';
    },
    // 获取组的分片数量
    getShardCount(gid) {
      return this.shards.filter(s => s === parseInt(gid)).length;
    },
    // 计算组的平均角度
    getGroupAngle(gid) {
      const shardIndices = this.shards
        .map((val, idx) => val === parseInt(gid) ? idx : -1)
        .filter(idx => idx !== -1);
      if (shardIndices.length === 0) return 0;
      const middleIndex = Math.floor(shardIndices.length / 2);
      return this.getMiddleAngle(shardIndices[middleIndex], this.shards.length);
    },
    // 查询配置
    handleQuery() {
      query(this.queryParams.num)
        .then(response => {
          this.config = response.data;
          this.showMessage('查询配置成功', 'success');
          this.showQueryModal = false;
        })
        .catch(error => {
          this.showMessage(`查询失败: ${error.message}`, 'error');
        });
    },
    // 加入组
    handleJoin() {
      if (!this.joinParams.gid || !this.joinParams.servers) {
        this.showMessage('请填写完整的组ID和服务器列表', 'error');
        return;
      }
      const servers = this.joinParams.servers.split(',').map(Number);
      const requestData = {
        servers: {
          [this.joinParams.gid]: servers
        }
      };
      join(requestData)
        .then(() => {
          this.showMessage(`组 ${this.joinParams.gid} 加入成功`, 'success');
          this.showJoinModal = false;
          this.refreshLatestConfig();
        })
        .catch(error => {
          this.showMessage(`加入失败: ${error.message}`, 'error');
        });
    },
    // 离开组
    handleLeave() {
      if (!this.leaveParams.gids) {
        this.showMessage('请填写组ID列表', 'error');
        return;
      }
      const gids = this.leaveParams.gids.split(',').map(Number);
      const requestData = {
        gids: gids
      };
      leave(requestData)
        .then(() => {
          this.showMessage(`组 ${this.leaveParams.gids} 离开成功`, 'success');
          this.showLeaveModal = false;
          this.refreshLatestConfig();
        })
        .catch(error => {
          this.showMessage(`离开失败: ${error.message}`, 'error');
        });
    },
    // 移动分片
    handleMove() {
      if (this.moveParams.shard === null || this.moveParams.gid === null) {
        this.showMessage('请填写完整的分片ID和目标组ID', 'error');
        return;
      }
      const requestData = {
        shard: Number(this.moveParams.shard),
        gid: Number(this.moveParams.gid)
      };
      move(requestData)
        .then(() => {
          this.showMessage(`分片 ${this.moveParams.shard} 移动到组 ${this.moveParams.gid} 成功`, 'success');
          this.showMoveModal = false;
          this.refreshLatestConfig();
        })
        .catch(error => {
          this.showMessage(`移动失败: ${error.message}`, 'error');
        });
    },
    // 刷新最新配置
    refreshLatestConfig() {
      query(-1)
        .then(response => {
          this.config = response.data;
        })
        .catch(error => {
          console.error('刷新配置失败:', error);
        });
    },
    // 显示消息提示
    showMessage(msg, type = 'success') {
      this.message = msg;
      this.messageType = type;
      setTimeout(() => {
        this.message = '';
      }, 3000);
    },
  },
  mounted() {
    this.refreshLatestConfig();
  },
};
</script>

<style scoped>
/* 自定义按钮样式 */
.niceButton4 {
  background-color: skyblue;
  border: none;
  border-radius: 12px;
  color: white;
  padding: 15px 32px;
  text-align: center;
  text-decoration: none;
  display: inline-block;
  font-size: 16px;
  margin: 4px 2px;
  cursor: pointer;
  transition-duration: 0.4s;
  -webkit-transition-duration: 0.4s;
}

.niceButton4:hover {
  box-shadow: 0 12px 16px 0 rgba(0, 0, 0, 0.24), 0 17px 50px 0 rgba(0, 0, 0, 0.19);
}

/* 动画效果 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.fade-enter,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* 输入框聚焦效果 */
input:focus {
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

/* 分片卡片悬停效果 */
.grid.grid-cols-5>div {
  white-space: normal;
  word-break: break-word;
  padding: 8px;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .grid.grid-cols-5 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .grid.grid-cols-5 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>