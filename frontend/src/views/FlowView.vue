<script setup lang="ts">
// 学习流首页（/flow）：数据来自真实 API（useFlowData），区块随数据显隐，无数据时给出引导。
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import { useFlowData } from '../composables/useFlowData'
import FlowSection from '../components/FlowSection.vue'
import KnowledgeFlowCard from '../components/KnowledgeFlowCard.vue'

const router = useRouter()

const { loading, data } = useFlowData()

/** 指标全为 0（候选/知识库/笔记/任务都为空）时视为"空学习流"。 */
const isEmpty = computed(
  () =>
    !data.value.candidate &&
    data.value.domains.length === 0 &&
    data.value.focus.metrics.every((m) => m.value === 0),
)

/** 侧栏是否需要展示：有知识库、有索引统计、或有最近活跃任一项。 */
const hasSideContent = computed(
  () =>
    data.value.domains.length > 0 ||
    data.value.indexed + data.value.processing + data.value.failed > 0 ||
    data.value.recent.length > 0,
)

function goChat() {
  void router.push('/chat')
}
function goCandidates() {
  void router.push('/candidates')
}
function goKnowledge() {
  void router.push('/knowledge-bases')
}
function metricValue(label: string): number {
  return data.value.focus.metrics.find((metric) => metric.label === label)?.value ?? 0
}
/** 最近活跃跳转：对话进 /chat，知识条目进详情页。 */
function goOpen(r: { type: 'conversation' | 'knowledge'; id: string }) {
  void router.push(r.type === 'conversation' ? '/chat' : `/documents/${r.id}`)
}
/** 最近活跃的时间格式化：今天显示 HH:mm，昨天显示「昨天」，更早显示 M 月 d 日。 */
function formatTime(iso: string): string {
  const t = new Date(iso)
  if (Number.isNaN(t.getTime())) return ''
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfDay = new Date(t.getFullYear(), t.getMonth(), t.getDate()).getTime()
  const pad = (n: number) => String(n).padStart(2, '0')
  const hm = `${pad(t.getHours())}:${pad(t.getMinutes())}`
  if (startOfDay >= startOfToday) return hm
  if (startOfDay === startOfToday - 86400000) return '昨天'
  return `${t.getMonth() + 1} 月 ${t.getDate()} 日`
}
</script>

<template>
  <div class="layout" :class="{ 'without-aside': !hasSideContent }">
    <main class="main">
      <section class="route">
        <FlowSection :focus="data.focus" />
        <KnowledgeFlowCard :flow="data.flow" />
      </section>

      <div v-if="loading" class="loading-hint">正在整理你的学习数据…</div>

      <section v-else-if="data.candidate" class="pending-block">
        <div class="pending-head">
          <h3>待你确认</h3>
          <button class="ghost-btn" @click="goCandidates">查看全部待沉淀 →</button>
        </div>
        <article class="candidate">
          <div class="candidate-top">
            <span class="tag-hot">待确认</span>
            <span class="candidate-source">知识候选 · AI 提炼</span>
          </div>
          <h4>{{ data.candidate.title }}</h4>
          <p>{{ data.candidate.summary }}</p>
          <div class="tags">
            <span v-for="tag in data.candidate.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>
          <div class="candidate-actions">
            <button class="ink-btn" @click="goCandidates">查看并确认</button>
            <button class="ghost-btn" @click="goCandidates">先修改</button>
          </div>
        </article>
      </section>

      <section v-else-if="isEmpty" class="empty">
        <div class="empty-guide">
          <div class="guide-head">
            <h3>从这里开始你的第一条知识流</h3>
            <p>知流会把对话里真正值得保留的理解，变成以后还能再次调用的个人知识。</p>
          </div>
          <div class="guide-steps">
            <div class="guide-card">
              <span class="guide-num">01</span>
              <h4>开启一段 AI 对话</h4>
              <p>聊聊正在学的东西，聊完点击「提取知识」。</p>
              <button class="ink-btn" @click="goChat">去 AI 对话 →</button>
            </div>
            <div class="guide-card">
              <span class="guide-num">02</span>
              <h4>建立你的知识库</h4>
              <p>手动记录笔记，或上传 Markdown / TXT 文件。</p>
              <button class="ghost-btn" @click="goKnowledge">创建知识库 →</button>
            </div>
          </div>
        </div>

        <div v-if="data.recent.length" class="empty-recent">
          <div class="recent-head">
            <h3>你的最近活跃</h3>
          </div>
          <ul class="recent-list">
            <li v-for="r in data.recent" :key="r.type + r.id" class="recent-row">
              <span class="recent-type" :class="r.type">{{
                r.type === 'conversation' ? '对话' : '知识'
              }}</span>
              <button class="recent-link" @click="goOpen(r)">{{ r.title }}</button>
              <time class="recent-time">{{ formatTime(r.updatedAt) }}</time>
            </li>
          </ul>
        </div>
      </section>

      <section v-if="!loading" class="next-block" aria-labelledby="next-title">
        <div class="next-head">
          <div>
            <span>KEEP THE FLOW</span>
            <h3 id="next-title">接下来，让知识继续流动</h3>
          </div>
          <p>每次只推进一小步，理解就会慢慢变成你自己的知识。</p>
        </div>
        <div class="next-grid">
          <button class="next-card" type="button" @click="goChat">
            <span class="next-index">01 / 对话</span>
            <strong>继续聊一个正在理解的问题</strong>
            <small>{{ data.recent.length }} 条最近活动可继续衔接</small>
            <em>开始对话 →</em>
          </button>
          <button class="next-card" type="button" @click="goCandidates">
            <span class="next-index">02 / 提炼</span>
            <strong>整理等待确认的知识候选</strong>
            <small>{{ metricValue('条待沉淀') }} 条内容等待你的判断</small>
            <em>去审核 →</em>
          </button>
          <button class="next-card" type="button" @click="goKnowledge">
            <span class="next-index">03 / 沉淀</span>
            <strong>新建或维护你的知识库</strong>
            <small>{{ metricValue('个知识库') }} 个知识库正在承接积累</small>
            <em>管理知识 →</em>
          </button>
        </div>
      </section>
    </main>

    <aside v-if="hasSideContent" class="aside">
      <section class="side-section">
        <div class="side-head">
          <h3>你的知识索引</h3>
          <button class="side-cta" @click="goKnowledge">管理 →</button>
        </div>
        <div class="indexbox">
          <div v-for="d in data.domains" :key="d.name" class="domain">
            <span class="domainname"><i :style="{ background: d.color }" />{{ d.name }}</span>
            <em v-if="d.count > 0">{{ d.count }}</em>
          </div>
        </div>
      </section>

      <section class="side-section">
        <div class="side-head">
          <h3>索引进度</h3>
        </div>
        <div class="health-row">
          <span class="health-dot health-dot-green" />
          <span class="health-label">已索引</span>
          <b>{{ data.indexed }}</b>
        </div>
        <div class="health-row">
          <span class="health-dot health-dot-yellow" />
          <span class="health-label">处理中</span>
          <b>{{ data.processing }}</b>
        </div>
        <div class="health-row">
          <span class="health-dot health-dot-red" />
          <span class="health-label">失败</span>
          <b>{{ data.failed }}</b>
        </div>
      </section>

      <section v-if="data.recent.length" class="side-section">
        <div class="side-head">
          <h3>最近活跃</h3>
          <button class="side-cta" @click="goChat">去对话 →</button>
        </div>
        <ul class="recent-list">
          <li v-for="r in data.recent" :key="r.type + r.id" class="recent-row">
            <span class="recent-type" :class="r.type">{{
              r.type === 'conversation' ? '对话' : '知识'
            }}</span>
            <button class="recent-link" @click="goOpen(r)">{{ r.title }}</button>
            <time class="recent-time">{{ formatTime(r.updatedAt) }}</time>
          </li>
        </ul>
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
.layout.without-aside {
  grid-template-columns: minmax(0, 1fr);
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

.loading-hint {
  color: var(--kf-muted);
  font-size: 12px;
  font-weight: 700;
  padding: 12px 0;
}

/* ---- 待沉淀卡 ---- */
.pending-block {
  margin-top: 8px;
}
.pending-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.pending-head h3 {
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 0.02em;
  margin: 0;
}
.candidate {
  border: 1px solid var(--kf-ink);
  background: var(--kf-white);
  padding: 18px 18px 16px;
  position: relative;
  box-shadow: 5px 5px 0 var(--kf-green);
  border-radius: 18px 5px 18px 5px;
}
.candidate-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.tag-hot {
  font-size: 9px;
  border: 1px solid var(--kf-hot);
  color: var(--kf-hot);
  border-radius: 999px;
  padding: 4px 8px;
  transform: rotate(2deg);
  font-weight: 900;
}
.candidate-source {
  font-size: 9px;
  color: var(--kf-muted);
  font-weight: 800;
}
.candidate h4 {
  font-size: 17px;
  font-weight: 900;
  line-height: 1.4;
  letter-spacing: -0.5px;
  margin: 0 0 9px;
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
  margin-top: 14px;
}
.candidate-actions button {
  min-height: 44px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 900;
  cursor: pointer;
}
.candidate-actions button:focus-visible,
.ghost-btn:focus-visible {
  outline: var(--kf-focus-ring);
  outline-offset: 2px;
}
.ink-btn {
  border: 1px solid var(--kf-ink);
  background: var(--kf-ink);
  color: var(--kf-paper);
}
.ghost-btn {
  border: 1px solid var(--kf-ink);
  background: transparent;
  color: var(--kf-ink);
  font-weight: 900;
  cursor: pointer;
  border-radius: 10px;
  min-height: 40px;
  padding: 0 12px;
  font-size: 10px;
}

/* ---- 空状态：分步引导 ---- */
.empty {
  margin-top: 12px;
}
.empty-guide {
  border: 1px solid var(--kf-ink);
  background: var(--kf-white);
  border-radius: 20px;
  padding: 34px 34px 40px;
  box-shadow: 6px 6px 0 var(--kf-red);
}
.guide-head {
  text-align: center;
  margin-bottom: 26px;
}
.guide-head h3 {
  font-size: 20px;
  font-weight: 900;
  margin: 0 0 10px;
  letter-spacing: -0.3px;
}
.guide-head p {
  font-size: 12px;
  line-height: 1.9;
  color: var(--kf-muted);
  margin: 0;
  font-weight: 600;
  max-width: 520px;
  margin-left: auto;
  margin-right: auto;
}
.guide-steps {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  max-width: 720px;
  margin: 0 auto;
}
.guide-card {
  border: 1px solid var(--kf-ink);
  border-radius: 16px;
  padding: 20px 20px 22px;
  background: var(--kf-paper);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.guide-num {
  font-size: 11px;
  font-weight: 900;
  color: var(--kf-hot);
  letter-spacing: 0.08em;
  border: 1px solid var(--kf-hot);
  border-radius: 999px;
  padding: 3px 8px;
}
.guide-card h4 {
  font-size: 15px;
  font-weight: 900;
  margin: 4px 0 0;
  letter-spacing: -0.2px;
}
.guide-card p {
  font-size: 11px;
  line-height: 1.8;
  color: var(--kf-muted);
  margin: 0 0 6px;
  font-weight: 600;
}
.guide-card button {
  min-height: 44px;
  padding: 0 16px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 900;
  cursor: pointer;
}

/* ---- 知识索引 aside ---- */
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

/* ---- 索引进度 ---- */
.health-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--kf-line-dashed);
}
.health-row b {
  font-size: 12px;
  font-weight: 900;
}
.health-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  border: 1px solid var(--kf-ink);
}
.health-dot-green {
  background: var(--kf-green);
}
.health-dot-yellow {
  background: var(--kf-yellow);
}
.health-dot-red {
  background: var(--kf-hot);
}
.health-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--kf-muted);
}

/* ---- 最近活跃 ---- */
.recent-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.recent-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--kf-line-dashed);
}
.recent-type {
  font-size: 9px;
  font-weight: 900;
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid var(--kf-line);
  flex: 0 0 auto;
}
.recent-type.conversation {
  color: var(--kf-hot);
  border-color: var(--kf-hot);
}
.recent-type.knowledge {
  color: var(--kf-green);
  border-color: var(--kf-green);
}
.recent-link {
  border: 0;
  background: transparent;
  padding: 0;
  text-align: left;
  font: inherit;
  font-size: 11px;
  font-weight: 800;
  color: var(--kf-ink);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.recent-link:hover {
  color: var(--kf-hot);
}
.recent-time {
  font-size: 9px;
  color: var(--kf-muted);
  font-weight: 700;
  flex: 0 0 auto;
}
.empty-recent {
  margin-top: 22px;
  border: 1px solid var(--kf-line);
  background: var(--kf-white);
  border-radius: 16px;
  padding: 16px 18px;
  max-width: 720px;
  margin-left: auto;
  margin-right: auto;
}

/* ---- 下一步：用真实数据承接首屏下方空间 ---- */
.next-block {
  margin-top: 34px;
  padding-top: 24px;
  border-top: 2px solid var(--kf-ink);
}
.next-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}
.next-head span {
  color: var(--kf-hot);
  font-size: 9px;
  font-weight: 900;
  letter-spacing: 0.16em;
}
.next-head h3 {
  margin: 5px 0 0;
  font-size: 20px;
  font-weight: 900;
  letter-spacing: -0.5px;
}
.next-head p {
  max-width: 340px;
  margin: 0;
  color: var(--kf-muted);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.7;
  text-align: right;
}
.next-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.next-card {
  min-height: 176px;
  padding: 18px;
  border: 1px solid var(--kf-ink);
  border-radius: var(--kf-radius-lg);
  background: var(--kf-white);
  color: var(--kf-ink);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  transition:
    color var(--kf-duration-fast) var(--kf-ease),
    background var(--kf-duration-fast) var(--kf-ease),
    transform var(--kf-duration-fast) var(--kf-ease),
    box-shadow var(--kf-duration-fast) var(--kf-ease);
}
.next-card:hover,
.next-card:focus-visible {
  background: var(--kf-ink);
  color: var(--kf-paper);
  transform: translateY(-3px);
  box-shadow: 5px 5px 0 var(--kf-red);
}
.next-index {
  color: var(--kf-hot);
  font-size: 9px;
  font-weight: 900;
  letter-spacing: 0.08em;
}
.next-card strong {
  margin-top: 18px;
  font-size: 15px;
  line-height: 1.45;
  font-weight: 900;
}
.next-card small {
  margin-top: 7px;
  color: var(--kf-muted);
  font-size: 10px;
  font-weight: 700;
}
.next-card:hover small,
.next-card:focus-visible small {
  color: var(--kf-line);
}
.next-card em {
  margin-top: auto;
  font-size: 10px;
  font-style: normal;
  font-weight: 900;
}
.recent-head h3 {
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.02em;
  margin: 0 0 4px;
}
.side-cta {
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--kf-ink);
  border-radius: 13px;
  background: var(--kf-ink);
  color: var(--kf-white);
  box-shadow: 3px 3px 0 var(--kf-red);
  font-size: 11px;
  font-weight: 900;
  cursor: pointer;
  transition:
    transform var(--kf-duration-fast) var(--kf-ease),
    background var(--kf-duration-fast) var(--kf-ease);
}
.side-cta:hover,
.side-cta:focus-visible {
  background: var(--kf-red);
  transform: translateY(-2px);
}

@media (max-width: 1200px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .aside {
    display: none;
  }
}
@media (max-width: 760px) {
  .next-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .next-head p {
    text-align: left;
  }
  .next-grid {
    grid-template-columns: 1fr;
  }
}
</style>
