<template>
    <div class="container mx-auto px-4 py-8 max-w-7xl">
        <h1 class="text-3xl font-bold mb-6 text-gray-800">分布式事务测试工具</h1>
        <!-- 操作控制区 -->
        <div class="bg-white rounded-xl shadow-md p-6 mb-6 border border-gray-100">
            <div class="flex flex-wrap items-center gap-4 mb-4">
                <div class="flex items-center">
                    <label class="mr-2 text-gray-700">操作类型:</label>
                    <select v-model="newTransaction.op"
                        class="border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition">
                        <option value="1">Execute (2PC)</option>
                        <option value="2">Crash</option>
                        <option value="3">Get</option>
                    </select>
                </div>
                <div class="flex items-center">
                    <label class="mr-2 text-gray-700">Primary节点:</label>
                    <input v-model="newTransaction.primary" type="text"
                        class="border border-gray-300 rounded-lg px-4 py-2 w-40 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition"
                        placeholder="输入primary节点">
                </div>
                <button @click="addTransaction"
                    class="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2 rounded-lg shadow-md transition flex items-center gap-1">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M10 5a1 1 0 011 1v3h3a1 1 0 110 2h-3v3a1 1 0 11-2 0v-3H6a1 1 0 110-2h3V6a1 1 0 011-1z"
                            clip-rule="evenodd" />
                    </svg>
                    添加事务
                </button>
                <button @click="executeTransactions" :disabled="!transactions.length"
                    class="bg-green-600 hover:bg-green-700 text-white px-5 py-2 rounded-lg shadow-md transition disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l3-2z"
                            clip-rule="evenodd" />
                    </svg>
                    执行事务 ({{ transactions.length }})
                </button>
                <button @click="clearAll"
                    class="bg-gray-500 hover:bg-gray-600 text-white px-5 py-2 rounded-lg shadow-md transition flex items-center gap-1">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z"
                            clip-rule="evenodd" />
                    </svg>
                    清空
                </button>
            </div>
            <!-- 条目编辑区 -->
            <div v-if="newTransaction.op !== '4'" class="mt-6">
                <h3 class="text-lg font-medium mb-3 text-gray-800">条目列表</h3>
                <div v-for="(entry, index) in newTransaction.entries" :key="index" class="flex items-center gap-3 mb-3">
                    <input v-model="entry.key" placeholder="Key"
                        class="w-24 border border-gray-300 rounded-lg px-2 py-1 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition">
                    <input v-if="newTransaction.op !== '3'" v-model="entry.value" placeholder="Value"
                        class="w-24 border border-gray-300 rounded-lg px-2 py-1 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition">
                    <select v-model="entry.type"
                        class="border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition">
                        <option value="PUT">PUT</option>
                        <option value="GET">GET</option>
                    </select>
                    <button @click="removeEntry(index)"
                        class="bg-red-500 hover:bg-red-600 text-white px-3 py-2 rounded-lg shadow transition flex items-center">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd"
                                d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                <button @click="addEntry"
                    class="bg-gray-200 hover:bg-gray-300 px-4 py-2 rounded-lg shadow transition flex items-center gap-1 text-gray-700">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M10 5a1 1 0 011 1v3h3a1 1 0 110 2h-3v3a1 1 0 11-2 0v-3H6a1 1 0 110-2h3V6a1 1 0 011-1z"
                            clip-rule="evenodd" />
                    </svg>
                    添加条目
                </button>
            </div>
            <div v-else class="mt-6">
                <h3 class="text-lg font-medium mb-3 text-gray-800">GET操作</h3>
                <input v-model="newTransaction.entries[0].key" placeholder="Key"
                    class="border border-gray-300 rounded-lg px-4 py-2 w-full focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition">
            </div>
        </div>
        <!-- 事务列表和结果展示区 -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <!-- 事务列表 - 变窄 -->
            <div class="bg-white rounded-xl shadow-md p-6 border border-gray-100 lg:col-span-1">
                <h2 class="text-xl font-semibold mb-4 text-gray-800">
                    事务队列 ({{ transactions.length }})
                </h2>
                <div class="overflow-auto max-h-[600px]">
                    <draggable v-model="transactions" :item-key="txn => txn.txnId" handle=".drag-handle"
                        @end="updateTxnIds" class="space-y-3">
                        <template #item="{ element: txn, index }">
                            <div
                                class="bg-gray-50 rounded-lg p-4 border border-gray-200 hover:border-blue-300 transition cursor-move">
                                <div class="flex items-center justify-between">
                                    <div class="flex items-center gap-3">
                                        <span class="drag-handle text-gray-400 hover:text-gray-600 cursor-move">
                                            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none"
                                                viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M4 8h16M4 16h16" />
                                            </svg>
                                        </span>
                                        <span class="font-medium text-gray-700">TXN #{{ txn.txnId }}</span>
                                        <span :class="getOperationTypeClass(txn.op)"
                                            class="px-3 py-1 rounded-full text-sm">
                                            {{ getOperationName(txn.op) }}
                                        </span>
                                    </div>
                                    <button @click="removeTransaction(index)"
                                        class="text-red-500 hover:text-red-700 transition">
                                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20"
                                            fill="currentColor">
                                            <path fill-rule="evenodd"
                                                d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z"
                                                clip-rule="evenodd" />
                                        </svg>
                                    </button>
                                </div>
                                <div class="mt-2 pl-9">
                                    <div class="text-sm text-gray-600"><span class="font-medium">Primary:</span> {{
                                        txn.primary }}</div>
                                    <div v-if="txn.op === 3" class="text-sm text-gray-600">
                                        <span class="font-medium">Key:</span> {{ txn.entries[0].key }}
                                    </div>
                                    <div v-else>
                                        <div class="text-sm font-medium text-gray-600 mb-1">Entries:</div>
                                        <div v-for="(entry, idx) in txn.entries" :key="idx"
                                            class="text-sm bg-white rounded px-3 py-2 mb-1 border border-gray-200">
                                            <span class="font-medium">{{ entry.type }}</span> {{ entry.key }}
                                            <span v-if="entry.value">= {{ entry.value }}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </template>
                    </draggable>
                </div>
            </div>
            <!-- 执行结果 - 变宽 -->
            <div class="bg-white rounded-xl shadow-md p-6 border border-gray-100 lg:col-span-2">
                <h2 class="text-xl font-semibold mb-4 text-gray-800">
                    执行结果
                </h2>
                <!-- 瀑布流式日志展示 -->
                <div v-if="results.length > 0" class="mt-4">
                    <div class="flex items-start gap-4 overflow-x-auto pb-4 h-[600px] overflow-y-auto">
                        <!-- 每个事务的日志列 -->
                        <div v-for="txnId in orderedTxnIds" :key="txnId" class="w-62 flex-shrink-0">
                            <div class="sticky top-0 z-10 bg-white pb-2">
                                <div class="flex items-center justify-between mb-2">
                                    <h3 class="font-medium text-gray-800">事务 #{{ txnId }}</h3>
                                    <span :class="getTxnStatusClass(txnId)" class="px-2 py-1 rounded text-xs">
                                        {{ getTxnStatus(txnId) }}
                                    </span>
                                </div>
                                <div class="h-1 w-full bg-gray-200 rounded-full overflow-hidden">
                                    <div :class="getTxnColorClass(txnId)" :style="{ width: getTxnProgress(txnId) + '%' }"
                                        class="h-full transition-all duration-500"></div>
                                </div>
                            </div>
                            <!-- 事务日志列表 - 固定高度布局 -->
                            <div class="relative mt-2" :style="{ height: calculateLogColumnHeight(txnId) }">
                                <div v-for="log in groupedLogs[txnId]" :key="log.text"
                                    class="p-3 border-b border-gray-200 timeline-event absolute w-full"
                                    :class="[getLogClass(log.text), getTxnBorderClass(txnId)]" :style="{
                                        height: '100px',
                                        top: calculateLogPosition(log)
                                    }">
                                    <div class="flex items-start gap-2 h-full overflow-hidden">
                                        <div class="flex-shrink-0 mt-0.5">
                                            <span :class="getTxnColorClass(txnId)"
                                                class="w-3 h-3 rounded-full block"></span>
                                        </div>
                                        <div class="overflow-hidden">
                                            <div class="text-sm font-medium text-gray-800">
                                                {{ formatLogTitle(log.text) }}
                                            </div>
                                            <div class="text-xs text-gray-500 mt-1">
                                                {{ formatLogDetails(log.text) }}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div v-else class="text-center py-8 text-gray-500 h-[600px] flex items-center justify-center">
                    暂无执行结果，请先执行事务
                </div>
                <!-- 结果摘要 -->
                <div v-if="results.length" class="mt-6 p-4 bg-blue-50 rounded-lg border border-blue-100">
                    <div class="flex items-center justify-between">
                        <div>
                            <h3 class="text-lg font-medium text-gray-800">执行摘要</h3>
                            <div class="flex items-center gap-4 mt-2">
                                <div class="text-sm">
                                    <span class="text-gray-600">总事务数:</span>
                                    <span class="font-medium ml-1">{{ Object.keys(groupedLogs).length }}</span>
                                </div>
                                <div class="text-sm">
                                    <span class="text-gray-600">成功数:</span>
                                    <span class="font-medium text-green-600 ml-1">{{ successCount }}</span>
                                </div>
                                <div class="text-sm">
                                    <span class="text-gray-600">失败数:</span>
                                    <span class="font-medium text-red-600 ml-1">{{
                                        Object.keys(groupedLogs).length - successCount }}</span>
                                </div>
                            </div>
                        </div>
                        <div class="text-sm bg-white rounded-full px-3 py-1 border border-gray-200 shadow-sm">
                            <span class="text-gray-600">成功率:</span>
                            <span class="font-medium text-blue-600 ml-1">{{
                                Object.keys(groupedLogs).length > 0 ?
                                    Math.round((successCount / Object.keys(groupedLogs).length) * 100) : 0
                            }}%</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <!-- Toast提示 -->
        <transition name="fade">
            <div v-if="showToast" class="fixed top-5 left-1/2 transform -translate-x-1/2 z-50">
                <div class="bg-gray-800 text-white px-6 py-3 rounded-lg shadow-lg flex items-center gap-2">
                    <svg v-if="toastType === 'success'" xmlns="http://www.w3.org/2000/svg"
                        class="h-5 w-5 text-green-400" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 001.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                            clip-rule="evenodd" />
                    </svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-red-400" viewBox="0 0 20 20"
                        fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L10 10.586 8.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l2-2z"
                            clip-rule="evenodd" />
                    </svg>
                    {{ toastMessage }}
                </div>
            </div>
        </transition>
    </div>
</template>

<script>
import draggable from 'vuedraggable';
import { ref, reactive, computed } from 'vue';
import { transactionTest } from '@/api/system/transaction';

const extractTxnIdFromLog = (log) => {
    const match = log.match(/事务 (\d+)/);
    return match ? match[1] : '0';
};

export default {
    name: 'TransactionTester',
    components: {
        draggable
    },
    setup() {
        const newTransaction = reactive({
            op: '1',
            primary: '',
            entries: [
                { key: '', value: '', type: 'PUT' }
            ]
        });
        const transactions = ref([]);
        const results = ref([]);
        const showToast = ref(false);
        const toastMessage = ref('');
        const toastType = ref('success');

        // 每条日志的固定高度
        const LOG_ITEM_HEIGHT = 100;
        const LOG_ITEM_GAP = 5;

        const parseLogs = (logs) => {
            const parsedLogs = logs.map(log => {
                const txnId = extractTxnIdFromLog(log);
                return {
                    txnId,
                    text: log
                };
            });
            return parsedLogs.map((log, index) => ({ ...log, globalIndex: index }));
        };

        const groupedLogs = computed(() => {
            if (results.value.length === 0) return {};
            const logs = parseLogs(results.value[0].logs);
            const grouped = {};
            logs.forEach(log => {
                if (!grouped[log.txnId]) {
                    grouped[log.txnId] = [];
                }
                grouped[log.txnId].push(log);
            });
            return grouped;
        });

        const orderedTxnIds = computed(() => {
            if (results.value.length === 0) return [];
            const seen = new Set();
            const order = [];
            results.value[0].logs.forEach(log => {
                const txnId = extractTxnIdFromLog(log);
                if (!seen.has(txnId)) {
                    seen.add(txnId);
                    order.push(txnId);
                }
            });
            return order;
        });

        const successCount = computed(() => {
            if (results.value.length === 0) return 0;
            let count = 0;
            Object.values(groupedLogs.value).forEach(logs => {
                const lastLog = logs[logs.length - 1];
                if (lastLog && lastLog.text.includes('提交成功')) {
                    count++;
                }
            });
            return count;
        });

        const calculateLogColumnHeight = (txnId) => {
            const logs = groupedLogs.value[txnId] || [];
            return (logs.length * (LOG_ITEM_HEIGHT + LOG_ITEM_GAP)) + 'px';
        };

        const calculateLogPosition = (log) => {
            return (log.globalIndex * (LOG_ITEM_HEIGHT + LOG_ITEM_GAP)) + 'px';
        };

        const getOperationName = (op) => {
            switch (op) {
                case 1:
                    return '执行2PC提交';
                case 2:
                    return '模拟Crash';
                case 3:
                    return '执行Get';
                default:
                    return 'Unknown';
            }
        };

        const addEntry = () => {
            newTransaction.entries.push({ key: '', value: '', type: 'PUT' });
        };

        const removeEntry = (index) => {
            newTransaction.entries.splice(index, 1);
        };

        const addTransaction = () => {
            if (!newTransaction.primary) {
                showToastMessage('请指定Primary节点', 'error');
                return;
            }
            if (newTransaction.op !== '3' && newTransaction.entries.some(e => !e.key)) {
                showToastMessage('请填写所有条目的Key', 'error');
                return;
            }
            if (newTransaction.op === '3' && !newTransaction.entries[0].key) {
                showToastMessage('请指定要GET的Key', 'error');
                return;
            }
            const txn = {
                txnId: transactions.value.length + 1,
                op: parseInt(newTransaction.op),
                primary: newTransaction.primary,
                entries: JSON.parse(JSON.stringify(newTransaction.entries.map(e => ({
                    key: e.key,
                    value: e.value,
                    cf: 'default',
                    type: e.type
                }))))
            };
            transactions.value.push(txn);
            newTransaction.primary = '';
            newTransaction.entries = [{ key: '', value: '', type: 'PUT' }];
            showToastMessage('事务已添加', 'success');
        };

        const removeTransaction = (index) => {
            transactions.value.splice(index, 1);
            updateTxnIds();
            showToastMessage('事务已删除', 'success');
        };

        const updateTxnIds = () => {
            transactions.value.forEach((txn, index) => {
                txn.txnId = index + 1;
            });
        };

        const executeTransactions = async () => {
            if (transactions.value.length === 0) {
                showToastMessage('没有要执行的事务', 'error');
                return;
            }
            try {
                const payload = {
                    txns: transactions.value.map(txn => ({
                        txnId: txn.txnId,
                        op: txn.op,
                        primary: txn.primary,
                        entries: txn.entries
                    }))
                };
                const response = await transactionTest(payload);
                results.value = [{
                    txnId: 'batch',
                    status: 'success',
                    result: '批量执行完成',
                    logs: response.data.logs
                }];
                showToastMessage('事务执行完成', 'success');
            } catch (error) {
                console.error('执行失败:', error);
                showToastMessage('事务执行失败: ' + (error.message || error), 'error');
            }
        };

        const clearAll = () => {
            transactions.value = [];
            results.value = [];
            showToastMessage('已清空所有事务和结果', 'success');
        };

        const showToastMessage = (message, type = 'success') => {
            toastMessage.value = message;
            toastType.value = type;
            showToast.value = true;
            setTimeout(() => {
                showToast.value = false;
            }, 3000);
        };

        const getOperationTypeClass = (op) => {
            return {
                1: 'bg-green-100 text-green-800',
                2: 'bg-red-100 text-red-800',
                3: 'bg-blue-100 text-blue-800'
            }[op];
        };

        const getTxnColorClass = (txnId) => {
            const colors = ['bg-blue-500', 'bg-green-500', 'bg-purple-500', 'bg-yellow-500', 'bg-pink-500'];
            return colors[parseInt(txnId) % colors.length];
        };

        const getTxnBorderClass = (txnId) => {
            const colors = ['border-blue-200', 'border-green-200', 'border-purple-200', 'border-yellow-200', 'border-pink-200'];
            return colors[parseInt(txnId) % colors.length];
        };

        const getLogClass = (logText) => {
            if (logText.includes('成功')) return 'bg-green-50';
            if (logText.includes('失败') || logText.includes('错误') || logText.includes('crash')) return 'bg-red-50';
            if (logText.includes('冲突') || logText.includes('回滚')) return 'bg-yellow-50';
            if (logText.includes('重试')) return 'bg-blue-50';
            return 'bg-gray-50';
        };

        const getTxnStatus = (txnId) => {
            const logs = groupedLogs.value[txnId] || [];
            const lastLog = logs[logs.length - 1];
            if (!lastLog) return '未知';
            if (lastLog.text.includes('提交成功')) return '提交成功';
            if (lastLog.text.includes('回滚')) return '已回滚';
            if (lastLog.text.includes('crash')) return '已崩溃';
            return '进行中';
        };

        const getTxnStatusClass = (txnId) => {
            const status = getTxnStatus(txnId);
            return {
                '提交成功': 'bg-green-100 text-green-800',
                '已回滚': 'bg-red-100 text-red-800',
                '已崩溃': 'bg-gray-100 text-gray-800',
                '进行中': 'bg-blue-100 text-blue-800'
            }[status];
        };

        const getTxnProgress = (txnId) => {
            const logs = groupedLogs.value[txnId] || [];
            if (logs.length === 0) return 0;
            const status = getTxnStatus(txnId);
            if (status === '提交成功') return 100;
            if (status === '已回滚' || status === '已崩溃') return 80;
            const hasCommit = logs.some(log => log.text.includes('commit'));
            return hasCommit ? 60 : 30;
        };

        const formatLogTitle = (log) => {
            const parts = log.split(';');
            return parts[0].replace(/事务 \d+ /, '');
        };

        const formatLogDetails = (log) => {
            const parts = log.split(';');
            return parts.length > 1 ? parts.slice(1).join(',').trim() : '';
        };

        return {
            newTransaction,
            transactions,
            results,
            showToast,
            toastMessage,
            toastType,
            successCount,
            groupedLogs,
            orderedTxnIds,
            calculateLogColumnHeight,
            calculateLogPosition,
            getOperationName,
            addEntry,
            removeEntry,
            addTransaction,
            removeTransaction,
            executeTransactions,
            clearAll,
            showToastMessage,
            getOperationTypeClass,
            getTxnColorClass,
            getTxnBorderClass,
            getLogClass,
            getTxnStatus,
            getTxnStatusClass,
            getTxnProgress,
            formatLogTitle,
            formatLogDetails,
            updateTxnIds
        };
    }
};
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
    transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
    opacity: 0;
}

.drag-handle:hover {
    color: #3b82f6;
}

.timeline-event {
    transition: all 0.3s ease;
    transform-origin: top;
}

.timeline-event:hover {
    transform: scale(1.02);
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

/* 支持日志内容换行 */
.timeline-event .text-sm {
    white-space: pre-line;
    word-break: break-all;
}

/* 自定义滚动条 */
::-webkit-scrollbar {
    width: 8px;
    height: 8px;
}

::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 4px;
}

::-webkit-scrollbar-thumb {
    background: #c1c1c1;
    border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
    background: #a8a8a8;
}

.h-[600px] {
    scrollbar-width: thin;
    scrollbar-color: #c1c1c1 #f1f1f1;
}
</style>