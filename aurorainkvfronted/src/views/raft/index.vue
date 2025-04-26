<template>
  <div class="raft-visualization">
    <h1>Raft Leader 选举过程可视化</h1>

    <div class="controls">
      <button @click="toggleAutoScroll">{{ autoScroll ? '暂停自动滚动' : '恢复自动滚动' }}</button>
      <button @click="clearAllHistory" class="clear-btn">清空所有任期</button>
      <span class="current-term">当前任期: {{ currentTerm }}</span>
      <span class="current-leader" v-if="currentLeader">Leader: Node {{ currentLeader }}</span>
      <select v-model="viewTerm" @change="changeTermView">
        <option value="current">当前任期</option>
        <option v-for="term in sortedTermKeys" :value="term" :key="term">
          任期 {{ term }}
        </option>
      </select>
      <button @click="debugMode = !debugMode" class="debug-btn">
        {{ debugMode ? '隐藏调试' : '显示调试' }}
      </button>
    </div>

    <div v-if="debugMode" class="debug-info">
      <h3>调试信息</h3>
      <pre>Term History: {{ termHistory }}</pre>
      <pre>Current Nodes: {{ nodes }}</pre>
      <pre>Log Cache Size: {{ logCache.length }}</pre>
    </div>

    <div class="content-container">
      <div class="logs">
        <h2>选举关键日志 <span class="log-count">({{ filteredLogs.length }}条)</span></h2>

        <!-- 节点筛选器 -->
        <div class="log-filter">
          <div class="filter-label">节点筛选:</div>
          <select v-model="selectedNode" @change="filterLogsByNode">
            <option value="all">所有节点</option>
            <option v-for="node in availableNodes" :value="node" :key="node">
              Node {{ node }}
            </option>
          </select>
          <button @click="clearNodeFilter" class="clear-filter-btn">清除筛选</button>
        </div>

        <ul ref="logList">
          <li v-for="(log, index) in filteredLogs" :key="index" :class="{
            'log-election': log.type === 'election',
            'log-vote': log.type === 'vote',
            'log-leader': log.type === 'leader',
            'log-term-change': log.type === 'term',
            'log-system': log.type === 'system'
          }">
            <span class="log-time">[{{ log.displayTime }}]</span>
            <span class="log-term">[任期 {{ log.term }}]</span>
            {{ log.text }}
          </li>
          <li v-if="filteredLogs.length === 0" class="no-logs">
            暂无日志数据，等待Raft集群通信...
          </li>
        </ul>
      </div>

      <div class="raft-state-container">
        <div class="term-info">
          <h3>任期 {{ viewTerm }} 详情</h3>
          <div v-if="viewTerm === 'current'">
            <p>当前任期: {{ currentTerm }}</p>
            <p v-if="currentLeader">Leader: Node {{ currentLeader }}</p>
            <p>节点状态:</p>
            <ul class="node-status-list">
              <li v-for="node in nodes" :key="node.id">
                Node {{ node.id }}:
                <span :class="{
                  'status-leader': node.state === 'leader',
                  'status-candidate': node.state === 'candidate',
                  'status-follower': node.state === 'follower'
                }">
                  {{ getStateName(node.state) }}
                </span>
                <span class="last-active" v-if="node.lastStateChange">
                  (最后活动: {{ formatTimeAgo(node.lastStateChange) }})
                </span>
              </li>
            </ul>
          </div>
          <div v-else>
            <p>该任期开始时间: {{ termHistory[viewTerm].startTime }}</p>
            <p v-if="termHistory[viewTerm].leader">Leader: Node {{ termHistory[viewTerm].leader }}</p>
            <p>选举结果: {{ termHistory[viewTerm].electionResult }}</p>
            <p v-if="Object.keys(termHistory[viewTerm].votes).length > 0">投票情况:</p>
            <ul class="vote-details">
              <li v-for="(votes, candidate) in termHistory[viewTerm].votes" :key="candidate">
                Node {{ candidate }} 获得 {{ votes.size }} 票
                <span v-if="votes.size > 0">(来自: {{ Array.from(votes).join(', ') }})</span>
              </li>
            </ul>
          </div>
        </div>

        <div class="raft-state">
          <h3>集群状态可视化</h3>
          <svg ref="raftSvg" width="100%" height="100%">
            <defs>
              <marker id="arrowhead" viewBox="0 -5 10 10" refX="25" markerWidth="6" markerHeight="6" orient="auto">
                <path d="M0,-5L10,0L0,5" fill="#4CAF50"></path>
              </marker>
            </defs>
            <text x="50%" y="50%" text-anchor="middle" v-if="nodes.length === 0" class="no-nodes">
              等待节点加入集群...
            </text>
          </svg>
        </div>
      </div>
    </div>

    <div class="term-timeline">
      <h3>任期历史时间线 (共 {{ Object.keys(termHistory).length }} 个任期)</h3>
      <div class="timeline-container">
        <div v-for="term in sortedTermKeys" :key="term" class="timeline-item" :class="{
          'timeline-current': term == currentTerm,
          'timeline-success': termHistory[term].electionResult === '成功',
          'timeline-failed': termHistory[term].electionResult === '失败'
        }" @click="viewTerm = term; displayTermHistory(term)">
          <div class="timeline-term">任期 {{ term }}</div>
          <div class="timeline-leader" v-if="termHistory[term].leader">Leader: {{ termHistory[term].leader }}</div>
          <div class="timeline-time">{{ termHistory[term].startTime }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as d3 from 'd3';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export default {
  name: "RaftVisualization",
  data() {
    return {
      logs: [],
      filteredLogs: [],
      nodes: [],
      links: [],
      stompClient: null,
      svg: null,
      simulation: null,
      currentTerm: 0,
      currentLeader: null,
      termHistory: {},
      viewTerm: 'current',
      votes: {},
      autoScroll: true,
      nodeSize: 30,
      debugMode: false,
      lastHeartbeatTimes: {},
      electionStartTimes: {},
      logCache: [],
      maxCacheSize: 1000,
      _isMounted: false,
      selectedNode: 'all',
      availableNodes: [],
      allNodes: new Set() // 新增：记录所有出现过的节点
    };
  },
  computed: {
    sortedTermKeys() {
      return Object.keys(this.termHistory)
        .map(Number)
        .sort((a, b) => b - a)
        .map(String);
    }
  },
  mounted() {
    this._isMounted = true;
    this.restoreState();
    this.initD3();
    this.initWebSocket();
  },
  methods: {
    initWebSocket() {
      try {
        const socket = new SockJS('http://localhost:8081/ws');
        this.stompClient = new Client({
          webSocketFactory: () => socket,
          onConnect: () => {
            if (!this._isMounted) return;

            console.log('WebSocket连接成功');
            this.stompClient.subscribe('/topic/logs', (messageOutput) => {
              if (!this._isMounted) return;
              this.$nextTick(() => {
                console.log(messageOutput.body);
                this.processLog(messageOutput.body);
              });
            });
          },
          onStompError: (frame) => {
            if (!this._isMounted) return;
            console.error('WebSocket连接错误:', frame);
            this.addSystemLog('WebSocket连接失败，请检查后端服务是否运行');
          },
          reconnectDelay: 5000,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000
        });
        this.stompClient.activate();
      } catch (e) {
        if (this._isMounted) {
          console.error('WebSocket初始化失败:', e);
          this.addSystemLog('WebSocket初始化失败: ' + e.message);
        }
      }
    },

    initD3() {
      if (!this._isMounted || !this.$refs.raftSvg) return;

      this.svg = d3.select(this.$refs.raftSvg)
        .attr("viewBox", `0 0 800 500`);

      this.simulation = d3.forceSimulation()
        .force("link", d3.forceLink().id(d => d.id).distance(150))
        .force("charge", d3.forceManyBody().strength(-500))
        .force("center", d3.forceCenter(400, 250))
        .force("x", d3.forceX(400).strength(0.05))
        .force("y", d3.forceY(250).strength(0.05))
        .force("collision", d3.forceCollide().radius(d => this.nodeSize + 5));

      this.updateGraph();
    },

    processLog(logMessage) {
      if (!this._isMounted) return;

      try {
        const now = new Date();
        const timestamp = now.toISOString();
        const displayTime = now.toLocaleTimeString();

        const logEntry = {
          text: logMessage,
          type: 'info',
          timestamp,
          displayTime,
          term: this.currentTerm
        };

        // 提取节点ID
        const nodeIdMatch = logMessage.match(/(?:raft|node)[:=](\d+)/i);
        const nodeId = nodeIdMatch ? nodeIdMatch[1] : null;

        // 记录所有出现过的节点
        if (nodeId) {
          this.allNodes.add(nodeId);
        }

        // 提取任期号
        const termMatch = logMessage.match(/任期(?:是|:)\s*(\d+)/) ||
          logMessage.match(/term[:=]\s*(\d+)/i);
        if (termMatch) {
          const newTerm = parseInt(termMatch[1]);
          logEntry.term = newTerm;

          if (!this.termHistory[newTerm]) {
            this.termHistory[newTerm] = this.createNewTermRecord(timestamp);
          }

          if (newTerm > this.currentTerm) {
            this.handleTermChange(newTerm, timestamp);
          }
        }

        // 状态变化检测
        if (logMessage.includes("成功当选 Leader") ||
          logMessage.match(/became Leader/i)) {
          this.handleNewLeader(nodeId, timestamp, logEntry.term);
          logEntry.type = 'leader';
          this.updateTermHistory({
            term: logEntry.term,
            leader: nodeId,
            electionResult: '成功',
            timestamp
          });
        }
        else if (logMessage.includes("转变为 Candidate") ||
          logMessage.match(/became Candidate/i)) {
          this.handleNewCandidate(nodeId, timestamp, logEntry.term);
          logEntry.type = 'election';
          this.updateTermHistory({
            term: logEntry.term,
            electionResult: '进行中',
            timestamp
          });
        }
        else if (logMessage.includes("转变为 Follower") ||
          logMessage.match(/became Follower/i)) {
          this.handleNewFollower(nodeId, timestamp, logEntry.term);
        }
        // 投票检测
        else if (logMessage.includes("同意投给") ||
          logMessage.match(/vote for/i)) {
          const matches = logMessage.match(/(?:raft|node)[:=](\d+).*(?:raft|node)[:=](\d+)/i);
          if (matches) {
            const [_, voterId, candidateId] = matches;
            this.handleVote(voterId, candidateId, timestamp);
            logEntry.type = 'vote';
          }
        }
        // 其他日志类型
        else if (logMessage.match(/(心跳超时|election timeout|开始选举|starting election)/i)) {
          logEntry.type = 'election';
        }
        else if (logMessage.match(/(收到心跳|received heartbeat)/i)) {
          if (nodeId) this.lastHeartbeatTimes[nodeId] = timestamp;
        }
        else if (logMessage.match(/(复制日志|append entries)/i)) {
          logEntry.type = 'replication';
        }
        else if (logMessage.match(/^(系统|System):/i)) {
          logEntry.type = 'system';
        }

        this.$nextTick(() => {
          if (!this._isMounted) return;
          this.logs.push(logEntry);
          this.cacheLog(logEntry);
          this.filterLogsByNode();
          if (this.autoScroll) this.scrollLogsToBottom();
        });

      } catch (e) {
        console.error('日志处理错误:', e, '内容:', logMessage);
        this.addSystemLog(`系统: 日志处理错误 - ${e.message}`);
      }
    },

    updateAvailableNodes() {
      this.availableNodes = Array.from(this.allNodes).sort();
    },

    filterLogsByNode() {
      if (this.selectedNode === 'all') {
        this.filteredLogs = this.logs.filter(log =>
          !['info', 'replication'].includes(log.type)
        );
      } else {
        this.filteredLogs = this.logs.filter(log =>
          !['info', 'replication'].includes(log.type) &&
          (log.text.includes(`node:${this.selectedNode}`) ||
            log.text.includes(`node=${this.selectedNode}`) ||
            log.text.includes(`raft:${this.selectedNode}`) ||
            log.text.includes(`raft=${this.selectedNode}`)
          ));
      }
      if (this.autoScroll) this.scrollLogsToBottom();
    },

    clearNodeFilter() {
      this.selectedNode = 'all';
      this.filterLogsByNode();
    },

    handleTermChange(newTerm, timestamp) {
      if (!this._isMounted || newTerm <= this.currentTerm) return;

      if (!this.termHistory[newTerm]) {
        this.termHistory[newTerm] = this.createNewTermRecord(timestamp);
      }

      this.currentTerm = newTerm;
      this.currentLeader = null;

      this.addSystemLog(`系统: 任期变更为 ${newTerm}`);
      this.updateGraph();
    },

    updateTermHistory({ term, leader = null, electionResult = null, timestamp }) {
      if (!this._isMounted) return;

      if (!this.termHistory[term]) {
        this.termHistory[term] = this.createNewTermRecord(timestamp);
      }

      const termRecord = this.termHistory[term];

      if (leader && (!termRecord.leaderTimestamp ||
        new Date(timestamp) > new Date(termRecord.leaderTimestamp))) {
        termRecord.leader = leader;
        termRecord.leaderTimestamp = timestamp;
        this.currentLeader = leader;
      }

      if (electionResult) {
        if (electionResult === '成功' ||
          termRecord.electionResult !== '成功') {
          termRecord.electionResult = electionResult;
        }
      }

      termRecord.nodes = JSON.parse(JSON.stringify(this.nodes));

      if (electionResult === '成功' && !termRecord.endTime) {
        termRecord.endTime = timestamp;
      }
    },

    handleVote(voterId, candidateId, timestamp) {
      if (!this._isMounted) return;

      if (!this.termHistory[this.currentTerm]) {
        this.termHistory[this.currentTerm] = this.createNewTermRecord(timestamp);
      }

      if (!this.termHistory[this.currentTerm].votes[candidateId]) {
        this.termHistory[this.currentTerm].votes[candidateId] = new Set();
      }

      this.termHistory[this.currentTerm].votes[candidateId].add(voterId);

      const voterNode = this.nodes.find(n => n.id === voterId);
      if (voterNode) {
        voterNode.lastVoteTime = timestamp;
      }

      this.updateGraph();
    },

    createNewTermRecord(timestamp) {
      return {
        term: this.currentTerm,
        startTime: new Date(timestamp).toLocaleTimeString(),
        startTimestamp: timestamp,
        endTime: null,
        leader: null,
        leaderTimestamp: null,
        electionResult: '未开始',
        votes: {},
        nodes: JSON.parse(JSON.stringify(this.nodes))
      };
    },

    handleNewLeader(leaderId, timestamp, term = this.currentTerm) {
      if (!this._isMounted) return;

      if (!this.termHistory[term]) {
        this.termHistory[term] = this.createNewTermRecord(timestamp);
      }

      const termRecord = this.termHistory[term];
      termRecord.leader = leaderId;
      termRecord.leaderTimestamp = timestamp;

      if (term === this.currentTerm) {
        this.currentLeader = leaderId;
      }

      // 确保所有节点都被记录
      this.allNodes.forEach(nodeId => {
        if (!this.nodes.some(n => n.id === nodeId)) {
          this.nodes.push({
            id: nodeId,
            state: 'follower',
            lastStateChange: timestamp
          });
        }
      });

      // 更新节点状态
      this.nodes.forEach(node => {
        if (node.id === leaderId) {
          node.state = 'leader';
          node.lastStateChange = timestamp;
        } else {
          node.state = 'follower';
          node.lastStateChange = timestamp;
        }
      });

      this.updateGraph();
    },

    handleNewCandidate(candidateId, timestamp, term = this.currentTerm) {
      if (!this._isMounted) return;

      if (!this.termHistory[term]) {
        this.termHistory[term] = this.createNewTermRecord(timestamp);
      }

      // 确保所有节点都被记录
      this.allNodes.forEach(nodeId => {
        if (!this.nodes.some(n => n.id === nodeId)) {
          this.nodes.push({
            id: nodeId,
            state: 'follower',
            lastStateChange: timestamp
          });
        }
      });

      // 更新候选节点状态
      const candidateNode = this.nodes.find(n => n.id === candidateId);
      if (candidateNode) {
        candidateNode.state = 'candidate';
        candidateNode.lastStateChange = timestamp;
      }

      this.updateGraph();
    },

    handleNewFollower(followerId, timestamp, term = this.currentTerm) {
      if (!this._isMounted) return;

      if (!this.termHistory[term]) {
        this.termHistory[term] = this.createNewTermRecord(timestamp);
      }

      // 确保所有节点都被记录
      this.allNodes.forEach(nodeId => {
        if (!this.nodes.some(n => n.id === nodeId)) {
          this.nodes.push({
            id: nodeId,
            state: 'follower',
            lastStateChange: timestamp
          });
        }
      });

      // 更新跟随者节点状态
      const followerNode = this.nodes.find(n => n.id === followerId);
      if (followerNode && followerNode.state !== 'leader') {
        followerNode.state = 'follower';
        followerNode.lastStateChange = timestamp;
      }

      this.updateGraph();
    },

    clearAllHistory() {
      if (!this._isMounted) return;

      if (confirm('确定要清空所有任期和日志记录吗？此操作不可撤销！')) {
        this.logs = [];
        this.filteredLogs = [];
        this.termHistory = {};
        this.votes = {};
        this.currentTerm = 0;
        this.currentLeader = null;
        this.nodes = [];
        this.selectedNode = 'all';
        this.availableNodes = [];
        this.allNodes = new Set();
        this.updateGraph();

        this.addSystemLog('所有任期和日志记录已清空');
      }
    },

    addSystemLog(message) {
      if (!this._isMounted) return;

      const now = new Date();
      const logEntry = {
        text: message,
        type: 'system',
        timestamp: now.toISOString(),
        displayTime: now.toLocaleTimeString(),
        term: this.currentTerm
      };

      this.logs.push(logEntry);
      this.cacheLog(logEntry);
      this.filterLogsByNode();
    },

    cacheLog(logEntry) {
      if (!this._isMounted) return;

      this.logCache.push(logEntry);
      if (this.logCache.length > this.maxCacheSize) {
        this.logCache.shift();
      }
      this.updateAvailableNodes();
    },

    formatTimeAgo(timestamp) {
      const now = new Date();
      const time = new Date(timestamp);
      const diff = now - time;

      const seconds = Math.floor(diff / 1000);
      if (seconds < 60) return `${seconds}秒前`;

      const minutes = Math.floor(seconds / 60);
      if (minutes < 60) return `${minutes}分钟前`;

      const hours = Math.floor(minutes / 60);
      if (hours < 24) return `${hours}小时前`;

      const days = Math.floor(hours / 24);
      return `${days}天前`;
    },

    getStateName(state) {
      const stateNames = {
        'leader': 'Leader',
        'candidate': 'Candidate',
        'follower': 'Follower'
      };
      return stateNames[state] || state;
    },

    changeTermView() {
      if (!this._isMounted) return;

      if (this.viewTerm === 'current') {
        this.nodes = this.termHistory[this.currentTerm]?.nodes || [];
        this.votes = {};
        this.updateGraph();
      } else {
        this.displayTermHistory(this.viewTerm);
      }

      this.filteredLogs = this.logs.filter(log =>
        String(log.term) === String(this.viewTerm === 'current' ? this.currentTerm : this.viewTerm)
      );

      if (this.selectedNode !== 'all') {
        this.filterLogsByNode();
      }
    },

    displayTermHistory(term) {
      if (!this._isMounted) return;

      const termLogs = this.logCache.filter(log =>
        String(log.term) === String(term)
      );

      // 获取该任期中的所有节点
      const nodesInTerm = new Set();
      termLogs.forEach(log => {
        const nodeIdMatch = log.text.match(/(?:raft|node)[:=](\d+)/i);
        if (nodeIdMatch) {
          nodesInTerm.add(nodeIdMatch[1]);
        }
      });

      // 合并所有出现过的节点
      const allNodesInTerm = Array.from(this.allNodes).map(id => ({
        id,
        state: 'follower', // 默认状态为follower
        lastStateChange: this.termHistory[term]?.startTimestamp || new Date().toISOString()
      }));

      // 更新节点状态
      const nodes = [...allNodesInTerm];
      const termRecord = this.termHistory[term] || {};

      // 如果有leader记录，更新leader状态
      if (termRecord.leader) {
        const leaderNode = nodes.find(n => n.id === termRecord.leader);
        if (leaderNode) {
          leaderNode.state = 'leader';
          leaderNode.lastStateChange = termRecord.leaderTimestamp;
        }
      }

      // 处理投票记录中的candidate状态
      if (termRecord.votes) {
        Object.keys(termRecord.votes).forEach(candidateId => {
          const candidateNode = nodes.find(n => n.id === candidateId);
          if (candidateNode && candidateNode.state !== 'leader') {
            candidateNode.state = 'candidate';
          }
        });
      }

      // 处理日志中的状态变化
      termLogs.forEach(log => {
        const nodeIdMatch = log.text.match(/(?:raft|node)[:=](\d+)/i);
        const nodeId = nodeIdMatch ? nodeIdMatch[1] : null;

        if (nodeId) {
          const node = nodes.find(n => n.id === nodeId);
          if (!node) return;

          if (log.text.includes("成功当选 Leader") ||
            log.text.match(/became Leader/i)) {
            node.state = 'leader';
            node.lastStateChange = log.timestamp;
          }
          else if (log.text.includes("转变为 Candidate") ||
            log.text.match(/became Candidate/i)) {
            node.state = 'candidate';
            node.lastStateChange = log.timestamp;
          }
          else if (log.text.includes("转变为 Follower") ||
            log.text.match(/became Follower/i)) {
            if (node.state !== 'leader') {
              node.state = 'follower';
              node.lastStateChange = log.timestamp;
            }
          }
        }
      });

      this.nodes = nodes;

      // 处理投票关系
      const votes = {};
      if (termRecord.votes) {
        Object.entries(termRecord.votes).forEach(([candidateId, voters]) => {
          if (!votes[candidateId]) {
            votes[candidateId] = new Set();
          }
          voters.forEach(voterId => {
            votes[candidateId].add(voterId);
          });
        });
      }

      this.votes = { [term]: votes };

      this.updateGraph();
    },

    updateGraph() {
      if (!this._isMounted || !this.$refs.raftSvg) return;

      try {
        if (!this.svg) {
          this.svg = d3.select(this.$refs.raftSvg);
          if (this.svg.empty()) return;
        }

        this.svg.selectAll("*").remove();

        this.svg.append("defs").html(`
          <marker id="arrowhead" viewBox="0 -5 10 10" refX="25" markerWidth="6" markerHeight="6" orient="auto">
            <path d="M0,-5L10,0L0,5" fill="#4CAF50"></path>
          </marker>
        `);

        if (this.nodes.length === 0) {
          this.svg.append("text")
            .attr("x", "50%")
            .attr("y", "50%")
            .attr("text-anchor", "middle")
            .attr("class", "no-nodes")
            .text("等待节点加入集群...");
          return;
        }

        const voteData = [];
        const displayTerm = this.viewTerm === 'current' ? this.currentTerm : this.viewTerm;
        const termVotes = this.votes[displayTerm] || {};

        Object.entries(termVotes).forEach(([candidateId, voters]) => {
          voters.forEach(voterId => {
            const sourceNode = this.nodes.find(n => n.id === voterId);
            const targetNode = this.nodes.find(n => n.id === candidateId);
            if (sourceNode && targetNode) {
              voteData.push({
                source: sourceNode,
                target: targetNode,
                value: 1,
                timestamp: this.termHistory[displayTerm]?.startTimestamp
              });
            }
          });
        });

        const link = this.svg.selectAll(".vote-link")
          .data(voteData, d => `${d.source.id}-${d.target.id}`)
          .join(
            enter => enter.append("line")
              .attr("class", "vote-link")
              .attr("marker-end", "url(#arrowhead)")
              .attr("stroke", "#4CAF50")
              .attr("stroke-width", 0)
              .attr("stroke-dasharray", "5,3")
              .attr("opacity", 0)
              .call(enter => enter.transition()
                .duration(800)
                .attr("stroke-width", 2)
                .attr("opacity", 0.7)
              ),
            update => update,
            exit => exit.remove()
          );

        const nodeGroups = this.svg.selectAll(".node-group")
          .data(this.nodes)
          .enter().append("g")
          .attr("class", "node-group")
          .call(d3.drag()
            .on("start", (event, d) => {
              if (!event.active) this.simulation.alphaTarget(0.3).restart();
              d.fx = d.x;
              d.fy = d.y;
            })
            .on("drag", (event, d) => {
              d.fx = event.x;
              d.fy = event.y;
            })
            .on("end", (event, d) => {
              if (!event.active) this.simulation.alphaTarget(0);
              d.fx = null;
              d.fy = null;
            }));

        nodeGroups.append("circle")
          .attr("class", "node")
          .attr("r", this.nodeSize)
          .attr("fill", d => {
            switch (d.state) {
              case 'leader': return "#2196F3";
              case 'candidate': return "#FF9800";
              default: return "#4CAF50";
            }
          })
          .attr("stroke", d => {
            switch (d.state) {
              case 'leader': return "#0D47A1";
              case 'candidate': return "#E65100";
              default: return "#2E7D32";
            }
          })
          .attr("stroke-width", d => d.state === 'leader' ? 3 : 2);

        nodeGroups.append("text")
          .attr("class", "node-label")
          .text(d => `Node ${d.id}`)
          .attr("dy", 4)
          .attr("text-anchor", "middle");

        if (!this.simulation) {
          this.simulation = d3.forceSimulation()
            .force("link", d3.forceLink().id(d => d.id).distance(150))
            .force("charge", d3.forceManyBody().strength(-500))
            .force("center", d3.forceCenter(400, 250))
            .force("x", d3.forceX(400).strength(0.05))
            .force("y", d3.forceY(250).strength(0.05))
            .force("collision", d3.forceCollide().radius(d => this.nodeSize + 5));
        }

        this.simulation
          .nodes(this.nodes)
          .force("link").links(voteData);

        this.simulation.on("tick", () => {
          link
            .attr("x1", d => d.source.x)
            .attr("y1", d => d.source.y)
            .attr("x2", d => d.target.x)
            .attr("y2", d => d.target.y);

          nodeGroups
            .attr("transform", d => `translate(${d.x},${d.y})`);
        });

        this.simulation.alpha(1).restart();
      } catch (e) {
        console.error('更新图表时出错:', e);
      }
    },

    scrollLogsToBottom() {
      this.$nextTick(() => {
        if (!this._isMounted || !this.$refs.logList) return;

        try {
          const logList = this.$refs.logList;
          logList.scrollTop = logList.scrollHeight;
        } catch (e) {
          console.error('滚动日志时出错:', e);
        }
      });
    },

    toggleAutoScroll() {
      if (!this._isMounted) return;

      this.autoScroll = !this.autoScroll;
      if (this.autoScroll) {
        this.scrollLogsToBottom();
      }
    },

    saveState() {
      const state = {
        logs: this.logs,
        nodes: this.nodes,
        currentTerm: this.currentTerm,
        termHistory: this.termHistory,
        selectedNode: this.selectedNode,
        availableNodes: this.availableNodes,
        allNodes: Array.from(this.allNodes)
      };
      localStorage.setItem('raftVisualizationState', JSON.stringify(state));
    },

    restoreState() {
      const saved = localStorage.getItem('raftVisualizationState');
      if (saved) {
        const state = JSON.parse(saved);
        this.logs = state.logs || [];
        this.nodes = state.nodes || [];
        this.currentTerm = state.currentTerm || 0;
        this.termHistory = state.termHistory || {};
        this.selectedNode = state.selectedNode || 'all';
        this.availableNodes = state.availableNodes || [];
        this.allNodes = new Set(state.allNodes || []);

        this.updateGraph();
        this.filterLogsByNode();
      }
    },
  },
  beforeUnmount() {
    this.saveState();
    this._isMounted = false;

    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }

    if (this.simulation) {
      this.simulation.stop();
      this.simulation = null;
    }

    this.svg = null;
  }
};
</script>

<style scoped>
.raft-visualization {
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  padding: 20px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8f9fa;
}

h1 {
  color: #2c3e50;
  margin-bottom: 15px;
  text-align: center;
}

.controls {
  display: flex;
  gap: 15px;
  align-items: center;
  margin-bottom: 15px;
  padding: 10px;
  background: #e9ecef;
  border-radius: 5px;
  flex-wrap: wrap;
}

button {
  padding: 8px 15px;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.3s;
}

button:hover {
  opacity: 0.9;
}

.clear-btn {
  background: #dc3545;
}

.clear-btn:hover {
  background: #c82333;
}

.debug-btn {
  background: #28a745;
  margin-left: auto;
}

.debug-btn:hover {
  background: #218838;
}

.current-term,
.current-leader {
  font-weight: bold;
  color: #495057;
}

.current-leader {
  color: #2196F3;
}

select {
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px solid #ced4da;
  background: white;
}

.content-container {
  display: flex;
  flex: 1;
  gap: 20px;
  height: calc(100% - 180px);
}

.logs {
  flex: 1;
  min-width: 300px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logs h2 {
  padding: 15px;
  margin: 0;
  background: #343a40;
  color: white;
  font-size: 1.1rem;
}

.logs ul {
  flex: 1;
  list-style: none;
  padding: 0;
  margin: 0;
  overflow-y: auto;
}

.logs li {
  padding: 12px 15px;
  border-bottom: 1px solid #eee;
  transition: all 0.2s;
}

.logs li:hover {
  background-color: #f8f9fa;
}

.log-election {
  border-left: 4px solid #FFC107;
  background-color: #fff8e1;
}

.log-vote {
  border-left: 4px solid #4CAF50;
  background-color: #e8f5e9;
}

.log-leader {
  border-left: 4px solid #2196F3;
  background-color: #e3f2fd;
  font-weight: bold;
}

.log-term-change {
  border-left: 4px solid #9C27B0;
  background-color: #f3e5f5;
}

.log-system {
  border-left: 4px solid #6c757d;
  background-color: #f8f9fa;
  font-style: italic;
}

.log-time {
  color: #6c757d;
  font-size: 0.8em;
  margin-right: 5px;
}

.log-term {
  color: #6c757d;
  font-size: 0.9em;
  margin-right: 5px;
}

.log-count {
  font-size: 0.8em;
  color: #6c757d;
  margin-left: 5px;
}

.no-logs,
.no-nodes {
  color: #6c757d;
  font-style: italic;
  text-align: center;
  padding: 20px;
}

.raft-state-container {
  display: flex;
  flex-direction: column;
  flex: 2;
  gap: 15px;
}

.term-info {
  background: white;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.node-status-list {
  list-style: none;
  padding: 0;
}

.node-status-list li {
  padding: 5px 0;
}

.status-leader {
  color: #2196F3;
  font-weight: bold;
}

.status-candidate {
  color: #FFC107;
  font-weight: bold;
}

.status-follower {
  color: #9E9E9E;
}

.last-active {
  font-size: 0.8em;
  color: #6c757d;
  margin-left: 5px;
}

.vote-details {
  list-style: none;
  padding: 0;
}

.vote-details li {
  padding: 3px 0;
}

.raft-state {
  flex: 1;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
}

.raft-state h3 {
  padding: 15px;
  margin: 0;
  background: #343a40;
  color: white;
  font-size: 1.1rem;
}

.raft-state svg {
  flex: 1;
  width: 100%;
  height: 100%;
  background: #f8f9fa;
}

.term-timeline {
  margin-top: 15px;
  background: white;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.timeline-container {
  display: flex;
  overflow-x: auto;
  gap: 10px;
  padding: 10px 0;
  min-height: 80px;
}

.timeline-item {
  min-width: 120px;
  padding: 10px;
  border-radius: 5px;
  background: #f8f9fa;
  cursor: pointer;
  transition: all 0.3s;
  border-left: 4px solid #dee2e6;
  flex-shrink: 0;
}

.timeline-item:hover {
  background: #e9ecef;
}

.timeline-current {
  border-left-color: #2196F3;
  background: #e3f2fd;
}

.timeline-success {
  border-left-color: #4CAF50;
}

.timeline-failed {
  border-left-color: #f44336;
}

.timeline-term {
  font-weight: bold;
  margin-bottom: 5px;
}

.timeline-leader {
  font-size: 0.9em;
  color: #2196F3;
  margin-bottom: 3px;
}

.timeline-time {
  font-size: 0.8em;
  color: #6c757d;
}

.debug-info {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 5px;
  margin-bottom: 15px;
  border: 1px solid #dee
}

.debug-info pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  font-size: 0.8em;
  background: white;
  padding: 10px;
  border-radius: 4px;
}

.logs::-webkit-scrollbar {
  width: 8px;
}

.logs::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.logs::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.logs::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

@media (max-width: 768px) {
  .content-container {
    flex-direction: column;
  }

  .logs,
  .raft-state {
    min-height: 300px;
  }
}

.vote-link {
  transition: opacity 0.5s ease-in-out;
}

.node {
  transition:
    fill 0.3s ease-in-out,
    stroke 0.3s ease-in-out,
    stroke-width 0.3s ease-in-out;
}

.log-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 15px;
  background: #f8f9fa;
  border-bottom: 1px solid #eee;
}

.filter-label {
  font-size: 0.9em;
  color: #495057;
}

.clear-filter-btn {
  padding: 5px 10px;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 0.8em;
  cursor: pointer;
}

.clear-filter-btn:hover {
  background: #5a6268;
}

/* 确保select元素样式一致 */
.log-filter select {
  padding: 5px 10px;
  border-radius: 4px;
  border: 1px solid #ced4da;
  background: white;
  font-size: 0.9em;
}
</style>