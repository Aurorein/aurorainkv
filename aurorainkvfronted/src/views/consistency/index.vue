<template>
    <div class="container mx-auto px-4 py-8">
        <h1 class="text-2xl font-bold mb-6">数据一致性验证</h1>

        <!-- 操作控制区 -->
        <div class="bg-white rounded-lg shadow p-6 mb-6">
            <div class="flex flex-wrap items-center gap-4 mb-4">
                <div class="flex items-center">
                    <label class="mr-2">操作数量:</label>
                    <input v-model.number="operationCount" type="number" min="1" max="100"
                        class="border rounded px-3 py-1 w-20">
                </div>

                <div class="flex items-center">
                    <label class="mr-2">GET比例:</label>
                    <input v-model.number="getRatio" type="number" min="0" max="100"
                        class="border rounded px-3 py-1 w-20">
                    <span class="ml-1">%</span>
                </div>

                <button @click="generateOperations" class="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded">
                    生成随机操作
                </button>

                <button @click="executeOperations" :disabled="!operations.length"
                    class="bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded disabled:opacity-50">
                    执行操作
                </button>

                <button @click="clearAll" class="bg-gray-500 hover:bg-gray-600 text-white px-4 py-2 rounded">
                    清空
                </button>
            </div>

            <div class="flex items-center">
                <input v-model="verifyConsistency" type="checkbox" id="verifyCheckbox" class="mr-2">
                <label for="verifyCheckbox">验证一致性</label>
            </div>
        </div>

        <!-- 操作和结果展示区 -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <!-- 操作序列 -->
            <div class="bg-white rounded-lg shadow p-6">
                <h2 class="text-lg font-semibold mb-4">
                    操作序列 ({{ operations.length }})
                </h2>
                <div class="overflow-auto max-h-96">
                    <table class="min-w-full divide-y divide-gray-200">
                        <thead class="bg-gray-50">
                            <tr>
                                <th
                                    class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    类型</th>
                                <th
                                    class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    键</th>
                                <th
                                    class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    值</th>
                            </tr>
                        </thead>
                        <tbody class="bg-white divide-y divide-gray-200">
                            <tr v-for="(op, index) in operations" :key="index">
                                <td class="px-6 py-4 whitespace-nowrap">
                                    <span :class="getOperationTypeClass(op.type)">
                                        {{ op.type }}
                                    </span>
                                </td>
                                <td class="px-6 py-4 whitespace-nowrap">{{ op.key }}</td>
                                <td class="px-6 py-4 whitespace-nowrap">{{ op.value || '-' }}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- 执行结果 -->
            <div class="bg-white rounded-lg shadow p-6">
                <h2 class="text-lg font-semibold mb-4">
                    执行结果 ({{ results.length }})
                </h2>
                <div class="overflow-auto max-h-96">
                    <table class="min-w-full divide-y divide-gray-200">
                        <thead class="bg-gray-50">
                            <tr>
                                <th
                                    class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    操作</th>
                                <th
                                    class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    结果</th>
                                <th v-if="verifyConsistency"
                                    class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    状态
                                </th>
                            </tr>
                        </thead>
                        <tbody class="bg-white divide-y divide-gray-200">
                            <tr v-for="(result, index) in resultsWithVerification" :key="index">
                                <td class="px-6 py-4 whitespace-nowrap">{{ result.operation }}</td>
                                <td class="px-6 py-4 whitespace-nowrap">{{ result.result }}</td>
                                <td v-if="verifyConsistency" class="px-6 py-4 whitespace-nowrap">
                                    <span :class="getStatusClass(result.status)">
                                        {{ result.status }}
                                    </span>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <!-- 结果摘要 -->
                <div v-if="results.length" class="mt-4 p-4 bg-gray-50 rounded">
                    <p class="mb-2">总操作数: {{ operations.length }}</p>
                    <p class="mb-2">成功数: {{ successCount }}</p>
                    <p v-if="verifyConsistency" class="flex items-center">
                        一致性验证:
                        <span :class="getConsistencyClass(isConsistent)" class="ml-2">
                            {{ isConsistent ? '全部一致' : '存在不一致' }}
                        </span>
                    </p>
                </div>
            </div>
        </div>

        <!-- Toast提示 -->
        <div v-if="showToast" class="toast">
            {{ toastMessage }}
        </div>
    </div>
</template>

<script>
import { batch } from '@/api/system/kv';

export default {
  name: 'BatchOperations',
  data() {
    return {
      operationCount: 10,
      getRatio: 30,
      verifyConsistency: true,
      operations: [],
      results: [],
      showToast: false,
      toastMessage: '',
      // 固定key集合
      fixedKeys: ['a', 'b', 'c'],
      // 内存中的预期状态
      expectedState: {},
      // 操作预期结果
      operationExpectations: []
    };
  },
  computed: {
    resultsWithVerification() {
      return this.results.map((r, i) => {
        const op = this.operations[i];
        const expected = this.operationExpectations[i]?.expected;
        
        return {
          operation: `${op.type} ${op.key}${op.type === 'PUT' ? '='+op.value : ''}`,
          result: r,
          expected: expected !== undefined ? expected : '-',
          status: this.operationExpectations[i]?.status || '-',
          isConsistent: this.operationExpectations[i]?.isConsistent
        };
      });
    },
    successCount() {
      return this.results.filter(r => 
        typeof r === 'string' ? 
        r.includes('SUCCESS') : 
        r.result.includes('SUCCESS')
      ).length;
    },
    isConsistent() {
      if (!this.verifyConsistency) return true;
      return this.operationExpectations.every(op => op.isConsistent !== false);
    },
    consistencyRate() {
      const total = this.operationExpectations.length;
      if (total === 0) return '100%';
      const consistent = this.operationExpectations.filter(op => op.isConsistent).length;
      return `${Math.round((consistent / total) * 100)}%`;
    }
  },
  methods: {
    // 生成1-10的随机数字作为value
    randomValue() {
      return Math.floor(Math.random() * 10) + 1;
    },
    
    // 生成随机操作序列并预先计算预期结果
    generateOperations() {
      this.operations = [];
      this.expectedState = {};
      this.operationExpectations = [];
      
      // 初始化预期状态
      this.fixedKeys.forEach(key => {
        this.expectedState[key] = 'no key'; // 初始值为null
      });

      let putCount = 0;
      let getCount = 0;

      for (let i = 0; i < this.operationCount; i++) {
        // 计算当前GET比例
        const currentGetRatio = (getCount / (i + 1)) * 100;
        // 决定是GET还是PUT
        const isGet = (putCount > 0) && 
                      (currentGetRatio < this.getRatio) && 
                      (Math.random() * 100 < this.getRatio);
        
        // 从固定key集合中随机选择
        const key = this.fixedKeys[Math.floor(Math.random() * this.fixedKeys.length)];
        const operation = {
          type: isGet ? 'GET' : 'PUT',
          key,
          value: isGet ? '' : this.randomValue(),
          seq: i
        };

        // 预先计算预期结果
        const expectation = {
          operation: `${operation.type} ${operation.key}`,
          expected: null,
          status: 'pending',
          isConsistent: null
        };

        if (isGet) {
          // 对于GET操作，预期结果是当前内存状态的值
          expectation.expected = this.expectedState[key];
          getCount++;
        } else {
          // 对于PUT操作，预期结果是成功
          this.expectedState[key] = operation.value;
          expectation.expected = 'PUT_SUCCESS';
          putCount++;
        }

        this.operations.push(operation);
        this.operationExpectations.push(expectation);

        // 更新内存状态（如果是PUT操作）
        if (!isGet) {
          this.expectedState[key] = operation.value;
        }
      }

      this.showToastMessage(
        `已生成 ${this.operationCount} 个操作（${putCount} PUT, ${getCount} GET）`
      );
    },

    // 执行批量操作并验证结果
    async executeOperations() {
      if (this.operations.length === 0) {
        this.showToastMessage('请先生成操作序列');
        return;
      }

      try {
        const response = await batch(this.operations);
        this.results = response.data;
        
        if (this.verifyConsistency) {
          this.verifyResults();
        }

        this.showToastMessage('操作执行完成');
      } catch (error) {
        console.error('执行失败:', error);
        this.showToastMessage('操作执行失败: ' + (error.message || error));
      }
    },

    // 验证实际结果与预期结果
    verifyResults() {
      this.operations.forEach((op, index) => {
        const actual = this.results[index];
        const expectation = this.operationExpectations[index];
        
        if (op.type === 'GET') {
          // 对于GET操作，比较实际结果与预期值
          expectation.isConsistent = actual == expectation.expected; // 宽松比较
          expectation.status = expectation.isConsistent ? '一致' : '不一致';
        } else {
          // 对于PUT操作，检查是否成功
          expectation.isConsistent = actual === 'PUT_SUCCESS';
          expectation.status = expectation.isConsistent ? '成功' : '失败';
        }
      });

      console.log('验证结果:', {
        operations: this.operations,
        expectedState: this.expectedState,
        expectations: this.operationExpectations,
        actualResults: this.results
      });
    },

    // 清空所有数据
    clearAll() {
      this.operations = [];
      this.results = [];
      this.expectedState = {};
      this.operationExpectations = [];
      this.showToastMessage('已清空所有数据和状态');
    },

    // 显示Toast消息
    showToastMessage(message) {
      this.toastMessage = message;
      this.showToast = true;
      setTimeout(() => {
        this.showToast = false;
      }, 3000);
    },

    // 获取操作类型样式类
    getOperationTypeClass(type) {
      return {
        'GET': 'bg-blue-100 text-blue-800 px-2 py-1 rounded',
        'PUT': 'bg-green-100 text-green-800 px-2 py-1 rounded',
        'APPEND': 'bg-yellow-100 text-yellow-800 px-2 py-1 rounded'
      }[type];
    },

    // 获取状态样式类
    getStatusClass(status) {
      return {
        '一致': 'bg-green-100 text-green-800 px-2 py-1 rounded',
        '不一致': 'bg-red-100 text-red-800 px-2 py-1 rounded',
        '成功': 'bg-green-100 text-green-800 px-2 py-1 rounded',
        '失败': 'bg-red-100 text-red-800 px-2 py-1 rounded',
        '-': 'bg-gray-100 text-gray-800 px-2 py-1 rounded'
      }[status];
    },

    // 获取一致性状态样式类
    getConsistencyClass(isConsistent) {
      return isConsistent 
        ? 'bg-green-100 text-green-800 px-2 py-1 rounded'
        : 'bg-red-100 text-red-800 px-2 py-1 rounded';
    }
  }
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

button {
    transition: all 0.2s ease-in-out;
}

button:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

button:disabled {
    transform: none;
    box-shadow: none;
}

/* 响应式调整 */
@media (max-width: 768px) {
    .grid {
        grid-template-columns: 1fr;
    }
}
</style>