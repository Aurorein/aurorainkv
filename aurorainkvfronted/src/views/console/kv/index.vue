<template>
  <div class="kv-app">
    <!-- Header -->
    <div class="header">
      <div class="logo">
        <i class="fas fa-database"></i>
      </div>
      <h1>Key-Value 存储引擎</h1>
      <!-- <p class="subtitle">高性能分布式键值存储系统</p> -->
    </div>

    <!-- Operations -->
    <div class="grid">
      <!-- PUT 操作卡片 -->
      <div class="operation-card put-card">
        <div class="card-header">
          <div class="icon-wrapper">
            <i class="fas fa-upload"></i>
          </div>
          <h2>存储数据</h2>
          <span class="operation-tag">PUT</span>
        </div>
        <div class="card-body">
          <div class="form-group">
            <label for="put-key">键名</label>
            <div class="input-wrapper">
              <input
                id="put-key"
                v-model="putData.key"
                placeholder="输入键名..."
                class="form-input"
                @input="updateShardInfo('put')"
              />
              <span class="input-icon">
                <i class="fas fa-key"></i>
              </span>
            </div>
          </div>
          <div class="shard-display" v-if="putShardInfo !== null">
            <div class="shard-badge">
              <i class="fas fa-cube"></i>
              <span>分片: {{ putShardInfo }}</span>
            </div>
          </div>
          <div class="form-group">
            <label for="put-value">键值</label>
            <div class="input-wrapper">
              <textarea
                id="put-value"
                v-model="putData.value"
                placeholder="输入键值..."
                class="form-textarea"
                rows="3"
              ></textarea>
              <span class="input-icon">
                <i class="fas fa-code"></i>
              </span>
            </div>
          </div>
          <button @click="handlePut" class="action-btn put-btn" :disabled="isPutLoading">
            <span v-if="!isPutLoading">
              <i class="fas fa-save"></i> 存储数据
            </span>
            <span v-else class="loading">
              <i class="fas fa-spinner fa-spin"></i> 处理中...
            </span>
          </button>
          <transition name="slide-fade">
            <div v-if="putResult" class="toast" :class="putResultClass">
              <i :class="putResultIcon"></i>
              {{ putResult }}
            </div>
          </transition>
        </div>
      </div>

      <!-- GET 操作卡片 -->
      <div class="operation-card get-card">
        <div class="card-header">
          <div class="icon-wrapper">
            <i class="fas fa-download"></i>
          </div>
          <h2>获取数据</h2>
          <span class="operation-tag">GET</span>
        </div>
        <div class="card-body">
          <div class="form-group">
            <label for="get-key">键名</label>
            <div class="input-wrapper">
              <input
                id="get-key"
                v-model="getData.key"
                placeholder="输入键名..."
                class="form-input"
                @input="updateShardInfo('get')"
              />
              <span class="input-icon">
                <i class="fas fa-key"></i>
              </span>
            </div>
          </div>
          <div class="shard-display" v-if="getShardInfo !== null">
            <div class="shard-badge">
              <i class="fas fa-cube"></i>
              <span>分片: {{ getShardInfo }}</span>
            </div>
          </div>
          <button @click="handleGet" class="action-btn get-btn" :disabled="isGetLoading">
            <span v-if="!isGetLoading">
              <i class="fas fa-search"></i> 查询数据
            </span>
            <span v-else class="loading">
              <i class="fas fa-spinner fa-spin"></i> 查询中...
            </span>
          </button>
          <transition name="slide-fade">
            <div v-if="getResult !== null" class="result-box">
              <div class="result-header">
                <i class="fas fa-file-alt"></i>
                <span>查询结果</span>
              </div>
              <div class="result-content">
                {{ getResult }}
              </div>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <!-- Recent Operations -->
    <div class="recent-ops" v-if="operationsHistory.length > 0">
      <div class="section-header">
        <i class="fas fa-history"></i>
        <h3>最近操作记录</h3>
      </div>
      <div class="history-container">
        <div v-for="(op, index) in operationsHistory" :key="index" class="history-item" :class="op.type">
          <div class="history-icon">
            <i :class="op.type === 'put' ? 'fas fa-upload' : 'fas fa-download'"></i>
          </div>
          <div class="history-details">
            <div class="history-main">
              <span class="op-key">{{ op.key }}</span>
              <span class="op-type">{{ op.type.toUpperCase() }}</span>
            </div>
            <div class="history-meta">
              <span class="op-time">{{ formatTime(op.timestamp) }}</span>
              <span class="op-status" :class="op.success ? 'success' : 'error'">
                <i :class="op.success ? 'fas fa-check-circle' : 'fas fa-times-circle'"></i>
                {{ op.success ? '成功' : '失败' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { get, put } from "@/api/system/kv";
export default {
  data() {
    return {
      putData: { key: "", value: "" },
      getData: { key: "" },
      putResult: "",
      getResult: null,
      isPutLoading: false,
      isGetLoading: false,
      operationsHistory: [],
      putShardInfo: null,
      getShardInfo: null,
      NShard: 10, // 分片数量
    };
  },
  computed: {
    putResultClass() {
      return this.putResult.includes("成功") ? "success-toast" : "error-toast";
    },
    putResultIcon() {
      return this.putResult.includes("成功") ? "fas fa-check-circle" : "fas fa-exclamation-circle";
    }
  },
  methods: {
    key2shard(key) {
      let shard = 0;
      if (key.length > 0) {
        shard = key.charCodeAt(0) - "a".charCodeAt(0);
      }
      return (shard % this.NShard + this.NShard) % this.NShard;
    },
    updateShardInfo(action) {
      const key = action === "put" ? this.putData.key : this.getData.key;
      if (key.trim()) {
        const shard = this.key2shard(key);
        if (action === "put") {
          this.putShardInfo = shard;
        } else {
          this.getShardInfo = shard;
        }
      } else {
        if (action === "put") {
          this.putShardInfo = null;
        } else {
          this.getShardInfo = null;
        }
      }
    },
    handlePut() {
      if (this.putData.key.trim() === "" || this.putData.value.trim() === "") {
        this.putResult = "错误: 键名和键值不能为空";
        // setTimeout(() => this.putResult = "", 3000);
        return;
      }
      this.isPutLoading = true;
      put(this.putData)
        .then((response) => {
          if (response?.status === 200) {
            this.putResult = "数据存储成功";
            this.recordOperation("put", this.putData.key, true);
            this.putData.value = "";
          } else {
            this.putResult = `存储失败: ${response?.message || "服务器错误"}`;
            this.recordOperation("put", this.putData.key, false);
          }
        })
        .catch((error) => {
          this.putResult = `请求出错: ${error.message}`;
          this.recordOperation("put", this.putData.key, false);
          console.error("PUT 操作出错:", error);
        })
        .finally(() => {
          this.isPutLoading = false;
        });
    },
    handleGet() {
      if (this.getData.key.trim() === "") {
        this.getResult = "错误: 请输入键名";
        // setTimeout(() => this.getResult = null, 3000);
        return;
      }
      this.isGetLoading = true;
      get(this.getData)
        .then((response) => {
          if (response?.status === 200) {
            this.getResult = response.data || "(空值)";
            this.recordOperation("get", this.getData.key, true);
          } else {
            this.getResult = `获取失败: ${response?.message || "键不存在"}`;
            this.recordOperation("get", this.getData.key, false);
          }
        })
        .catch((error) => {
          this.getResult = `请求出错: ${error.message}`;
          this.recordOperation("get", this.getData.key, false);
          console.error("GET 操作出错:", error);
        })
        .finally(() => {
          this.isGetLoading = false;
        });
    },
    recordOperation(type, key, success) {
      this.operationsHistory.unshift({
        type,
        key,
        success,
        timestamp: new Date(),
      });
      // 限制历史记录数量
      if (this.operationsHistory.length > 5) {
        this.operationsHistory.pop();
      }
    },
    formatTime(date) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    },
  },
};
</script>

<style scoped>
/* 基础样式 */
:root {
  --primary-color: #4361ee;
  --secondary-color: #3f37c9;
  --success-color: #4cc9f0;
  --info-color: #4895ef;
  --warning-color: #f72585;
  --light-color: #f8f9fa;
  --dark-color: #212529;
  --gray-color: #6c757d;
  --put-color: #7209b7;
  --get-color: #3a86ff;
  --put-light: rgba(114, 9, 183, 0.1);
  --get-light: rgba(58, 134, 255, 0.1);
}

* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
  background-color: #f5f7ff;
  color: var(--dark-color);
  line-height: 1.6;
}

.kv-app {
  max-width: 1200px;
  margin: 2rem auto;
  padding: 0 1rem;
}

/* 头部样式 */
.header {
  text-align: center;
  margin-bottom: 2.5rem;
  padding: 1.5rem;
  background: linear-gradient(135deg, #4361ee 0%, #3f37c9 100%);
  color: white;
  border-radius: 12px;
  box-shadow: 0 10px 20px rgba(67, 97, 238, 0.2);
}

.logo {
  font-size: 2.5rem;
  margin-bottom: 1rem;
  color: white;
}

.header h1 {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 0.5rem;
}

.subtitle {
  font-size: 1rem;
  opacity: 0.9;
  font-weight: 300;
}

/* 网格布局 */
.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
  margin-bottom: 2rem;
}

@media (min-width: 768px) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }
}

/* 操作卡片 */
.operation-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.05);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.operation-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
}

.put-card {
  border-top: 4px solid var(--put-color);
}

.get-card {
  border-top: 4px solid var(--get-color);
}

.card-header {
  display: flex;
  align-items: center;
  padding: 1.25rem 1.5rem;
  background-color: white;
  position: relative;
}

.icon-wrapper {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 1rem;
}

.put-card .icon-wrapper {
  background-color: var(--put-light);
  color: var(--put-color);
}

.get-card .icon-wrapper {
  background-color: var(--get-light);
  color: var(--get-color);
}

.card-header h2 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--dark-color);
}

.operation-tag {
  margin-left: auto;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.put-card .operation-tag {
  background-color: var(--put-light);
  color: var(--put-color);
}

.get-card .operation-tag {
  background-color: var(--get-light);
  color: var(--get-color);
}

.card-body {
  padding: 1.5rem;
}

/* 表单元素 */
.form-group {
  margin-bottom: 1.25rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--gray-color);
}

.input-wrapper {
  position: relative;
}

.input-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--gray-color);
  font-size: 0.9rem;
}

.form-input {
  width: 100%;
  padding: 0.75rem 1rem 0.75rem 2.5rem;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  font-size: 0.9375rem;
  transition: all 0.3s ease;
  background-color: #f8f9fa;
}

.form-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.2);
  background-color: white;
}

.form-textarea {
  width: 100%;
  padding: 0.75rem 1rem 0.75rem 2.5rem;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  font-size: 0.9375rem;
  min-height: 100px;
  resize: vertical;
  transition: all 0.3s ease;
  background-color: #f8f9fa;
}

.form-textarea:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.2);
  background-color: white;
}

/* 分片显示 */
.shard-display {
  margin: 0.75rem 0 1.25rem;
}

.shard-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.375rem 0.75rem;
  background-color: #f8f9fa;
  border-radius: 20px;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--gray-color);
}

.shard-badge i {
  margin-right: 0.375rem;
  color: var(--primary-color);
}

/* 按钮样式 */
.action-btn {
  width: 100%;
  padding: 0.875rem;
  border: none;
  border-radius: 8px;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1rem;
}

.action-btn i {
  margin-right: 0.5rem;
}

.put-btn {
  background-color: var(--put-color);
  color: white;
}

.put-btn:hover:not(:disabled) {
  background-color: #5a0b9e;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(114, 9, 183, 0.3);
}

.get-btn {
  background-color: var(--get-color);
  color: white;
}

.get-btn:hover:not(:disabled) {
  background-color: #2a75e6;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(58, 134, 255, 0.3);
}

.action-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}

.loading i {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 结果提示 */
.toast {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  padding: 0.875rem 1.5rem;
  border-radius: 8px;
  font-size: 0.9375rem;
  font-weight: 500;
  z-index: 1000;
  display: flex;
  align-items: center;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);
  animation: slideIn 0.3s ease-out;
}

.toast i {
  margin-right: 0.5rem;
}

.success-toast {
  background-color: #38b000;
  color: white;
}

.error-toast {
  background-color: #ef233c;
  color: white;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

/* 查询结果 */
.result-box {
  margin-top: 1.25rem;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  animation: fadeIn 0.3s ease;
}

.result-header {
  padding: 0.75rem 1rem;
  background-color: #f8f9fa;
  display: flex;
  align-items: center;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--gray-color);
  border-bottom: 1px solid #e9ecef;
}

.result-header i {
  margin-right: 0.5rem;
}

.result-content {
  padding: 1rem;
  background-color: white;
  font-size: 0.9375rem;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* 最近操作 */
.recent-ops {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.05);
}

.section-header {
  display: flex;
  align-items: center;
  margin-bottom: 1.25rem;
}

.section-header i {
  font-size: 1.25rem;
  margin-right: 0.75rem;
  color: var(--primary-color);
}

.section-header h3 {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--dark-color);
}

.history-container {
  display: grid;
  gap: 0.75rem;
}

.history-item {
  display: flex;
  align-items: center;
  padding: 0.875rem 1rem;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.history-item:hover {
  transform: translateX(5px);
}

.history-item.put {
  background-color: var(--put-light);
}

.history-item.get {
  background-color: var(--get-light);
}

.history-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 1rem;
  flex-shrink: 0;
}

.history-item.put .history-icon {
  background-color: rgba(114, 9, 183, 0.2);
  color: var(--put-color);
}

.history-item.get .history-icon {
  background-color: rgba(58, 134, 255, 0.2);
  color: var(--get-color);
}

.history-details {
  flex: 1;
  min-width: 0;
}

.history-main {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.25rem;
}

.op-key {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
  padding-right: 0.5rem;
}

.op-type {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.125rem 0.5rem;
  border-radius: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.history-item.put .op-type {
  background-color: rgba(114, 9, 183, 0.1);
  color: var(--put-color);
}

.history-item.get .op-type {
  background-color: rgba(58, 134, 255, 0.1);
  color: var(--get-color);
}

.history-meta {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: var(--gray-color);
}

.op-status {
  font-weight: 500;
}

.op-status i {
  margin-right: 0.25rem;
}

.op-status.success {
  color: #38b000;
}

.op-status.error {
  color: #ef233c;
}

/* 过渡动画 */
.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(1, 0.5, 0.8, 1);
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>