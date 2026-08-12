<script setup lang="ts">
// 学习流首页（/flow）：视觉迁移自 knowflow-preview-v3.html 的 flowView。
// ⚠️ 展示数据全部来自 src/mocks/mock-data.ts（集中 mock），未来接真实 API。
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { mockFlowData } from '../mocks/mock-data'
import FlowSection from '../components/FlowSection.vue'
import KnowledgeFlowCard from '../components/KnowledgeFlowCard.vue'

const router = useRouter()

const focus = mockFlowData.focus
const flow = mockFlowData.flow
const candidate = mockFlowData.candidate
const domains = mockFlowData.domains
const memory = mockFlowData.memory

// 学习 Workspace 的占位消息（mock）
const messages = [
  {
    who: '我',
    me: true,
    body: 'ConcurrentHashMap 为什么线程安全？',
  },
  {
    who: '知',
    me: false,
    body: '关键不是"用了锁"，而是锁的粒度和并发控制方式。以 JDK 8 为例，写入时结合 CAS 与 synchronized，把竞争尽量限制在单个桶节点上。',
    context: '本轮未调用个人知识库 · 使用当前会话上下文',
  },
  {
    who: '我',
    me: true,
    body: '那 JDK 7 呢？为什么后来要改？',
  },
]

const prompt = ref('')

const ragModes = ['个人知识：自动', '个人知识：关闭', '个人知识：强制']
const ragMode = ref(ragModes[0])

function cycleRagMode() {
  const idx = ragModes.indexOf(ragMode.value)
  ragMode.value = ragModes[(idx + 1) % ragModes.length]
}

function openCandidate() {
  void router.push('/candidates')
}
</script>

<template>
  <div class="layout">
    <main class="main">
      <section class="route">
        <FlowSection :focus="focus" />
        <KnowledgeFlowCard :flow="flow" />
      </section>

      <section class="workspace">
        <div class="workspace-head">
          <div class="thread-title">
            <span class="pulse" />
            ConcurrentHashMap：JDK 7 与 JDK 8
          </div>
          <div class="switches">
            <button class="pill" :class="{ active: true }" @click="cycleRagMode">
              {{ ragMode }}
            </button>
            <button class="pill">模型：DeepSeek</button>
            <button class="pill">会话记忆：开启</button>
          </div>
        </div>

        <div class="messages">
          <article v-for="(msg, i) in messages" :key="i" class="msg" :class="{ me: msg.me }">
            <div class="who">{{ msg.who }}</div>
            <div class="bubble">
              <span v-if="msg.context" class="contextflag">{{ msg.context }}</span>
              <p>{{ msg.body }}</p>
            </div>
          </article>
        </div>

        <div class="composer">
          <div class="composebox">
            <textarea v-model="prompt" placeholder="继续追问，或把今天真正没弄懂的地方写下来……" />
            <button class="send" aria-label="发送">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M5 12h13M13 6l6 6-6 6" stroke-width="1.8" />
              </svg>
            </button>
          </div>
          <div class="compose-meta">
            <div class="meta-left">
              <span><b>知识路由</b> 自动判断</span>
              <span><b>嵌入模型</b> 系统固定</span>
              <span><b>回答</b> 流式输出</span>
            </div>
            <span>⌘ + Enter 发送</span>
          </div>
        </div>
      </section>
    </main>

    <aside class="aside">
      <section class="side-section">
        <div class="side-head">
          <h3>待沉淀</h3>
          <span>1 条值得你判断</span>
        </div>
        <div class="candidate">
          <h4>{{ candidate.title }}</h4>
          <p>{{ candidate.summary }}</p>
          <div class="tags">
            <span v-for="tag in candidate.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>
          <div class="candidate-actions">
            <button>确认沉淀</button>
            <button @click="openCandidate">先修改</button>
          </div>
        </div>
      </section>

      <section class="side-section">
        <div class="side-head">
          <h3>你的知识索引</h3>
          <span>按知识域组织</span>
        </div>
        <div class="indexbox">
          <div v-for="d in domains" :key="d.name" class="domain">
            <span class="domainname"><i :style="{ background: d.color }" />{{ d.name }}</span>
            <em>{{ d.count }}</em>
          </div>
        </div>
      </section>

      <section class="side-section">
        <div class="side-head">
          <h3>当前会话记忆</h3>
          <span>短期上下文</span>
        </div>
        <div class="memory-card">
          <h4>{{ memory.title }}</h4>
          <p>{{ memory.text }}</p>
          <div class="tiny">{{ memory.hint }}</div>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  min-height: calc(100vh - var(--kf-mast-h));
}
.main {
  padding: 34px 34px 84px 38px;
  min-width: 0;
}
.aside {
  border-left: 1px solid var(--kf-line);
  padding: 34px 26px;
  background: rgba(235, 229, 216, 0.36);
}
.route {
  display: grid;
  grid-template-columns: 1.25fr 0.75fr;
  gap: 22px;
  margin-bottom: 28px;
}

/* ---- workspace ---- */
.workspace {
  border: 1px solid var(--kf-ink);
  background: rgba(255, 253, 248, 0.76);
  box-shadow: var(--kf-shadow-card);
  border-radius: 24px;
  overflow: hidden;
}
.workspace-head {
  min-height: 66px;
  border-bottom: 1px solid var(--kf-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 10px 20px;
  gap: 12px;
}
.thread-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 900;
  letter-spacing: -0.2px;
}
.thread-title .pulse {
  width: 9px;
  height: 9px;
  background: var(--kf-green);
  border-radius: 50%;
  box-shadow: 0 0 0 5px var(--kf-green-soft);
}
.switches {
  display: flex;
  gap: 7px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.pill {
  height: 32px;
  padding: 0 11px;
  border: 1px solid var(--kf-line);
  border-radius: 999px;
  background: transparent;
  color: var(--kf-muted);
  font-size: 10px;
  cursor: pointer;
  font-weight: 800;
}
.pill.active {
  background: var(--kf-green-soft);
  color: var(--kf-green);
  border-color: #94ac9b;
}

/* ---- messages ---- */
.messages {
  padding: 28px 23px 18px;
  min-height: 320px;
  background-image: linear-gradient(rgba(67, 62, 51, 0.042) 1px, transparent 1px);
  background-size: 100% 42px;
}
.msg {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 13px;
  margin-bottom: 25px;
  max-width: 880px;
}
.who {
  width: 31px;
  height: 31px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 900;
  border: 1px solid var(--kf-ink);
  background: var(--kf-white);
}
.msg.me {
  margin-left: auto;
  grid-template-columns: minmax(0, 1fr) 34px;
}
.msg.me .who {
  order: 2;
  background: var(--kf-ink);
  color: var(--kf-paper);
}
.msg.me .bubble {
  order: 1;
  background: var(--kf-paper-2);
  border-color: #bcb3a2;
  border-radius: 18px 5px 18px 18px;
}
.bubble {
  border: 1px solid var(--kf-line);
  background: var(--kf-white);
  padding: 16px 17px 17px;
  box-shadow: 2px 3px 0 rgba(23, 23, 19, 0.07);
  border-radius: 5px 18px 18px 18px;
}
.bubble p {
  font-size: 14px;
  line-height: 1.9;
  margin: 0;
  font-weight: 600;
}
.contextflag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  color: var(--kf-green);
  border-bottom: 1px solid #9aae9c;
  padding-bottom: 3px;
  margin-bottom: 10px;
  font-weight: 800;
}
.contextflag::before {
  content: '↳';
}

/* ---- composer ---- */
.composer {
  border-top: 1px solid var(--kf-line);
  padding: 14px;
  background: var(--kf-white);
}
.composebox {
  border: 1px solid var(--kf-ink);
  display: grid;
  grid-template-columns: 1fr auto;
  min-height: 92px;
  background: var(--kf-paper);
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 3px 3px 0 var(--kf-ink);
}
.composebox textarea {
  border: 0;
  resize: none;
  background: transparent;
  padding: 15px 16px;
  outline: none;
  font-size: 14px;
  line-height: 1.7;
  color: var(--kf-ink);
  font-weight: 700;
  font-family: inherit;
}
.composebox textarea::placeholder {
  color: #91897c;
}
.send {
  width: 64px;
  border: 0;
  border-left: 1px solid var(--kf-ink);
  background: var(--kf-hot);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: 0.2s;
}
.send:hover {
  background: var(--kf-yellow);
}
.send svg {
  width: 23px;
  height: 23px;
}
.compose-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  font-size: 9px;
  color: var(--kf-muted);
  font-weight: 700;
}
.meta-left {
  display: flex;
  gap: 14px;
}
.meta-left b {
  color: var(--kf-ink);
  font-weight: 900;
}

/* ---- aside ---- */
.side-section {
  margin-bottom: 30px;
}
.side-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.side-head h3 {
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.02em;
  margin: 0;
}
.side-head span {
  font-size: 9px;
  color: var(--kf-muted);
  font-weight: 700;
}
.candidate {
  border: 1px solid var(--kf-ink);
  background: var(--kf-white);
  padding: 16px 15px 14px;
  position: relative;
  box-shadow: 5px 5px 0 var(--kf-green);
  border-radius: 18px 5px 18px 5px;
}
.candidate::before {
  content: '待确认';
  position: absolute;
  right: 8px;
  top: 8px;
  font-size: 8px;
  border: 1px solid var(--kf-hot);
  color: var(--kf-hot);
  border-radius: 999px;
  padding: 4px 7px;
  transform: rotate(2deg);
  font-weight: 900;
}
.candidate h4 {
  font-size: 17px;
  font-weight: 900;
  line-height: 1.4;
  letter-spacing: -0.5px;
  margin: 1px 58px 9px 0;
}
.candidate p {
  font-size: 11px;
  line-height: 1.8;
  color: var(--kf-muted);
  margin: 0 0 12px;
  font-weight: 600;
}
.tags {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}
.tag {
  font-size: 8px;
  border: 1px solid var(--kf-line);
  padding: 5px 7px;
  background: var(--kf-paper);
  border-radius: 999px;
  font-weight: 800;
}
.candidate-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
  margin-top: 13px;
}
.candidate-actions button {
  height: 32px;
  border: 1px solid var(--kf-ink);
  background: transparent;
  font-size: 9px;
  cursor: pointer;
  border-radius: 10px;
  font-weight: 900;
}
.candidate-actions button:first-child {
  background: var(--kf-ink);
  color: var(--kf-paper);
}
.indexbox {
  border-top: 2px solid var(--kf-ink);
  padding-top: 9px;
}
.domain {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  padding: 11px 0;
  border-bottom: 1px dashed var(--kf-line-dashed);
}
.domainname {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  font-weight: 800;
}
.domainname i {
  display: block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--kf-ink);
}
.domain em {
  font-style: normal;
  font-size: 9px;
  color: var(--kf-muted);
  font-weight: 800;
}
.memory-card {
  background: var(--kf-green);
  color: #f3f0e8;
  padding: 17px;
  margin-top: 12px;
  position: relative;
  overflow: hidden;
  border-radius: 18px;
}
.memory-card::after {
  content: '记';
  position: absolute;
  right: 0;
  bottom: -26px;
  font-size: 88px;
  line-height: 1;
  font-weight: 900;
  color: rgba(255, 255, 255, 0.06);
}
.memory-card h4 {
  margin: 0 0 7px;
  font-size: 12px;
  font-weight: 900;
}
.memory-card p {
  font-size: 10px;
  line-height: 1.8;
  margin: 0;
  color: #d8ded5;
  font-weight: 600;
}
.memory-card .tiny {
  margin-top: 11px;
  font-size: 8px;
  color: #b7c6b8;
  font-weight: 800;
}
</style>
