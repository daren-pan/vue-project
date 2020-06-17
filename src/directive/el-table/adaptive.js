import { addResizeListener, removeResizeListener } from 'element-ui/src/utils/resize-event'

/**
 * How to use
 * <el-table height="100px" v-el-height-adaptive-table="{bottomOffset: 30}">...</el-table>
 * el-table height is must be set
 * bottomOffset: 30(default)   // The height of the table from the bottom of the page.
 */

const doResize = async(el, binding, vnode) => {
  const { componentInstance: $table } = await vnode

  const { value } = binding

  if (!$table.height) {
    throw new Error(`el-$table must set the height. Such as height='100px'`)
  }
  const bottomOffset = (value && value.bottomOffset) || 30

  if (!$table) return

  const height = window.innerHeight - el.getBoundingClientRect().top - bottomOffset
  $table.layout.setHeight(height)
  $table.doLayout()
}

export default {
  async bind(el, binding, vnode) {
    el.resizeListener = async() => {
      await doResize(el, binding, vnode)
    }
    // parameter 1 is must be "Element" type
    addResizeListener(el, el.resizeListener)
    addResizeListener(window.document.body, el.resizeListener)
  },
  async inserted(el, binding, vnode) {
    await doResize(el, binding, vnode)
  },
  unbind(el) {
    removeResizeListener(el, el.resizeListener)
  }
}
