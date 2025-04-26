<template>
    <div class="container mx-auto p-4">
        <h1 class="text-2xl font-bold mb-6">Raft一致性测试</h1>

        <!-- 测试控制区 -->
        <div class="bg-white p-4 rounded-lg shadow-md mb-6">
            <button @click="runTest" class="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded mr-4">
                执行测试
            </button>
            <span v-if="isTesting" class="text-blue-500">测试运行中...</span>
        </div>

        <!-- 结果展示区 -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <!-- 左侧：测试概览 -->
            <div class="bg-white p-4 rounded-lg shadow-md">
                <h2 class="text-xl font-semibold mb-4">测试概览</h2>
                <div v-if="testResult" class="space-y-4">
                    <div class="grid grid-cols-2 gap-4">
                        <div class="bg-gray-50 p-3 rounded">
                            <h3 class="font-medium">总轮次</h3>
                            <p class="text-2xl">{{ testResult.rounds.length }}</p>
                        </div>
                        <div :class="overallStatusClass">
                            <h3 class="font-medium">整体状态</h3>
                            <p class="text-2xl">{{ testResult.allRoundsValid ? '通过' : '失败' }}</p>
                        </div>
                    </div>

                    <div v-for="(round, index) in testResult.rounds" :key="index" class="border rounded p-3"
                        :class="round.valid ? 'border-green-200' : 'border-red-200'">
                        <div class="flex justify-between items-center">
                            <h3 class="font-medium">第 {{ index + 1 }} 轮</h3>
                            <span :class="round.valid ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
                                class="px-2 py-1 rounded text-sm">
                                {{ round.valid ? '通过' : '失败' }}
                            </span>
                        </div>
                    </div>
                </div>
                <div v-else class="text-gray-500">
                    暂无测试结果
                </div>
            </div>

            <!-- 右侧：详细日志展示 -->
            <div class="bg-white p-4 rounded-lg shadow-md">
                <h2 class="text-xl font-semibold mb-4">Raft操作日志</h2>

                <!-- 操作筛选 -->
                <div class="mb-4 flex items-center space-x-4">
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">操作类型</label>
                        <select v-model="filterType" class="border rounded px-3 py-1">
                            <option value="all">全部</option>
                            <option value="PUT">PUT</option>
                            <option value="GET">GET</option>
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">Key筛选</label>
                        <select v-model="filterKey" class="border rounded px-3 py-1">
                            <option value="all">全部</option>
                            <option v-for="key in availableKeys" :key="key" :value="key">{{ key }}</option>
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">验证状态</label>
                        <select v-model="filterValid" class="border rounded px-3 py-1">
                            <option value="all">全部</option>
                            <option value="valid">验证通过</option>
                            <option value="invalid">验证失败</option>
                        </select>
                    </div>
                </div>

                <!-- 日志表格 -->
                <div class="overflow-auto max-h-600px border rounded">
                    <table class="min-w-full divide-y divide-gray-200">
                        <thead class="bg-gray-50 sticky top-0">
                            <tr>
                                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">索引</th>
                                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">操作</th>
                                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Key</th>
                                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">值</th>
                                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">验证</th>
                                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">客户端</th>
                            </tr>
                        </thead>
                        <tbody class="bg-white divide-y divide-gray-200">
                            <tr v-for="op in filteredOperations" :key="op.logIndex"
                                :class="{ 'bg-red-50': op.type === 'GET' && !op.valid }">
                                <td class="px-4 py-2 text-sm">{{ op.logIndex }}</td>
                                <td class="px-4 py-2">
                                    <span :class="getOperationClass(op.type)" class="px-2 py-1 rounded text-xs">
                                        {{ op.type }}
                                    </span>
                                </td>
                                <td class="px-4 py-2 text-sm">{{ op.key }}</td>
                                <td class="px-4 py-2 text-sm font-mono">
                                    {{ op.type === 'GET' ? `${op.observedValue}` : op.observedValue }}
                                </td>
                                <td class="px-4 py-2">
                                    <span v-if="op.type === 'GET'"
                                        :class="op.valid ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
                                        class="px-2 py-1 rounded text-xs">
                                        {{ op.valid ? '✓' : `✗ (预期: ${op.expectedValue})` }}
                                    </span>
                                    <span v-else class="text-gray-400 text-xs">-</span>
                                </td>
                                <td class="px-4 py-2 text-sm">
                                    {{ op.clientId }} ({{ op.seqId }})
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <!-- 统计信息 -->
                <div v-if="testResult" class="mt-4 grid grid-cols-3 gap-4">
                    <div class="bg-blue-50 p-3 rounded">
                        <h3 class="text-sm font-medium">总操作数</h3>
                        <p class="text-xl">{{ totalOperations }}</p>
                    </div>
                    <div class="bg-green-50 p-3 rounded">
                        <h3 class="text-sm font-medium">GET验证通过</h3>
                        <p class="text-xl">{{ validGetCount }} ({{ getSuccessRate }}%)</p>
                    </div>
                    <div class="bg-red-50 p-3 rounded">
                        <h3 class="text-sm font-medium">GET验证失败</h3>
                        <p class="text-xl">{{ invalidGetCount }}</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import { concurrentTest } from '@/api/system/kv';

export default {
    name: 'RaftConsistencyTest',
    data() {
        return {
            isTesting: false,
            testResult: null,
            filterType: 'all',
            filterKey: 'all',
            filterValid: 'all',
            availableKeys: ['ct_a', 'ct_b', 'ct_c']
        }
    },
    computed: {
        overallStatusClass() {
            return {
                'p-3 rounded': true,
                'bg-green-50': this.testResult?.allRoundsValid,
                'bg-red-50': !this.testResult?.allRoundsValid
            }
        },
        allOperations() {
            if (!this.testResult) return [];
            return this.testResult.rounds.flatMap(round => round.operations);
        },
        filteredOperations() {
            return this.allOperations.filter(op => {
                // 类型筛选
                if (this.filterType !== 'all' && op.type !== this.filterType) return false;
                // Key筛选
                if (this.filterKey !== 'all' && op.key !== this.filterKey) return false;
                // 验证状态筛选
                if (this.filterValid !== 'all') {
                    if (op.type !== 'GET') return false;
                    if (this.filterValid === 'valid' && !op.valid) return false;
                    if (this.filterValid === 'invalid' && op.valid) return false;
                }
                return true;
            });
        },
        totalOperations() {
            return this.allOperations.length;
        },
        getOperations() {
            return this.allOperations.filter(op => op.type === 'GET');
        },
        validGetCount() {
            return this.getOperations.filter(op => op.valid).length;
        },
        invalidGetCount() {
            return this.getOperations.filter(op => !op.valid).length;
        },
        getSuccessRate() {
            if (this.getOperations.length === 0) return 0;
            return Math.round((this.validGetCount / this.getOperations.length) * 100);
        }
    },
    methods: {
        async runTest() {
            this.isTesting = true;
            try {
                const response = await concurrentTest();
                this.testResult = response.data;
            } catch (error) {
                console.error('测试失败:', error);
            } finally {
                this.isTesting = false;
            }
        },
        getOperationClass(type) {
            return {
                'PUT': 'bg-green-100 text-green-800',
                'GET': 'bg-blue-100 text-blue-800',
                'APPEND': 'bg-yellow-100 text-yellow-800'
            }[type];
        }
    }
}
</script>

<style scoped>
.container {
    max-width: 1200px;
}

table {
    font-size: 0.875rem;
}

tr:hover {
    background-color: #f5f5f5;
}

.sticky {
    position: sticky;
    top: 0;
    z-index: 10;
}

.max-h-600px {
    max-height: 600px;
}
</style>