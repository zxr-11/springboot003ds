<template>
  <div class="ai-page">
    <el-card class="ai-card">
      <div slot="header" class="ai-header">
        <div class="title">AI 助手</div>
        <div class="sub">在这里直接对话，内容会发送到后端接口 /ai/chat</div>
      </div>

      <div class="ai-messages" ref="msgBox">
        <div v-for="(m, idx) in messages" :key="idx" class="row" :class="m.role">
          <div class="bubble">
            <div class="role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
            <div class="content">{{ m.content }}</div>
            <div class="time" v-if="m.time">{{ m.time }}</div>
          </div>
        </div>
      </div>

      <div class="ai-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          placeholder="输入你想问的问题，Enter 发送，Shift+Enter 换行"
          @keydown.native="onKeydown"
        />
        <div class="actions">
          <el-button @click="clear" plain>清空</el-button>
          <el-button type="primary" :loading="loading" @click="send">发送</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  data() {
    return {
      input: '',
      loading: false,
      messages: [
        { role: 'assistant', content: '你好！我是 AI 助手。你可以直接问我项目怎么用、也可以让我帮你写点功能。', time: this.now() }
      ]
    }
  },
  methods: {
    now() {
      const d = new Date()
      const pad = (n) => (n < 10 ? '0' + n : '' + n)
      return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.msgBox
        if (el) el.scrollTop = el.scrollHeight
      })
    },
    onKeydown(e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        this.send()
      }
    },
    clear() {
      this.messages = [
        { role: 'assistant', content: '已清空。继续提问吧。', time: this.now() }
      ]
      this.scrollToBottom()
    },
    send() {
      const text = (this.input || '').trim()
      if (!text || this.loading) return

      this.messages.push({ role: 'user', content: text, time: this.now() })
      this.input = ''
      this.scrollToBottom()

      this.loading = true
      this.$http({
        url: `ai/chat`,
        method: 'post',
        data: { message: text }
      })
        .then(({ data }) => {
          if (data && data.code === 0) {
            this.messages.push({ role: 'assistant', content: data.data || '', time: this.now() })
          } else {
            this.messages.push({ role: 'assistant', content: (data && data.msg) ? data.msg : '请求失败', time: this.now() })
          }
        })
        .catch((err) => {
          this.messages.push({ role: 'assistant', content: err && err.message ? err.message : '网络异常', time: this.now() })
        })
        .finally(() => {
          this.loading = false
          this.scrollToBottom()
        })
    }
  },
  mounted() {
    this.scrollToBottom()
  }
}
</script>

<style scoped>
.ai-page {
  padding: 20px;
}
.ai-card {
  max-width: 980px;
  margin: 0 auto;
}
.ai-header .title {
  font-size: 18px;
  font-weight: 700;
}
.ai-header .sub {
  margin-top: 6px;
  font-size: 12px;
  color: #888;
}
.ai-messages {
  height: 520px;
  overflow: auto;
  background: #f6f7fb;
  padding: 16px;
  border-radius: 8px;
}
.row {
  display: flex;
  margin-bottom: 12px;
}
.row.user {
  justify-content: flex-end;
}
.row.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 78%;
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.04);
}
.row.user .bubble {
  background: #e8f3ff;
}
.role {
  font-size: 12px;
  color: #666;
  margin-bottom: 6px;
}
.content {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.6;
  color: #222;
}
.time {
  margin-top: 8px;
  font-size: 11px;
  color: #999;
  text-align: right;
}
.ai-input {
  margin-top: 14px;
}
.actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

