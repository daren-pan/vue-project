<template>
  <div class="icons-container">
    <div ref="terminalContainer" style="height:90vh; width: 100%;" />
  </div>
</template>

<script>
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import 'xterm/css/xterm.css'

export default {
  name: 'TerminalComponent',
  mounted() {
    this.initTerminal()
  },
  methods: {
    initTerminal() {
      const terminal = new Terminal()
      const fitAddon = new FitAddon()

      terminal.loadAddon(fitAddon)
      terminal.open(this.$refs.terminalContainer)
      fitAddon.fit()

      // 连接到 WebSocket 服务器
      const socket = new WebSocket('ws://localhost:2999')

      socket.onopen = () => {
        console.log('WebSocket 连接已建立')
      }

      socket.onmessage = (event) => {
        // 将服务器的响应显示在终端中
        terminal.write(event.data)
      }

      terminal.onData(data => {
        // 将终端的输入发送到服务器
        socket.send(data)
      })
    }
  }
}
</script>

<style lang="css" scoped>
.icons-container {
  margin: 10px 20px 0;
  overflow: hidden;
}
</style>
