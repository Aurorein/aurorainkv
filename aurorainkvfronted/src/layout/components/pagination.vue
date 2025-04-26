<template>
  <div>
    <ul>
      <li class="page" @click="handlePre">
        <icon-radix-icons:chevron-left class="iconStyle" />
      </li>
      <li
        :class="{ 'is-actived': item === currentPage, page: true }"
        v-for="(item, index) in pages"
        :key="index"
        @click="handleCurrentChange(item)">
        {{ item }}
      </li>
      <li class="page" @click="handleNext">
        <icon-radix-icons:chevron-right class="iconStyle" />
      </li>
    </ul>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'

const props = defineProps({
  total: {
    type: Number,
    required: true,
  },
  currPage: {
    type: Number,
    required: true,
  },
})

const emitData = defineEmits(['currentPageChange'])

onMounted(() => {
  totalPage.value = props.total
  currentPage.value = props.currPage
})

watch(
  () => props.total,
  async (newTotal, oldTotal) => {
    totalPage.value = newTotal
  }
)

watch(
  () => props.currPage,
  async (newCurr, oldCurr) => {
    currentPage.value = newCurr
  }
)

const currentPage = ref(12)
const totalPage = ref(30)
const pages = computed(() => {
  const t = totalPage.value
  const c = currentPage.value
  if (t <= 10) {
    return Array.from({ length: t }, (_, i) => i + 1)
  }

  if (c <= 5) {
    return [1, 2, 3, 4, 5, 6, 7, '...', t]
  }
  if (c >= t - 4) {
    return [1, '...', t - 6, t - 5, t - 4, t - 3, t - 2, t - 1, t]
  }
  return [1, '...', c - 2, c - 1, c, c + 1, c + 2, '...', t]
})

function handleCurrentChange(curr) {
  if (curr === currentPage.value) return
  if (typeof curr === 'string') return
  currentPage.value = curr
  emitData('currentPageChange', curr)
}

function handlePre() {
  if (currentPage.value === 1) return
  currentPage.value -= 1
  emitData('currentPageChange', currentPage.value)
}
function handleNext() {
  if (currentPage.value === totalPage.value) return
  currentPage.value += 1
  emitData('currentPageChange', currentPage.value)
}
</script>
<style scoped>
ul {
  display: flex;
}
ul li {
  list-style: none;
  cursor: pointer;
  color: #999999;
}
li:hover {
  background-color: rgb(224, 239, 255);
}
.page {
  display: flex;
  justify-content: center;
  align-items: center;
  line-height: 35px;
  width: 35px;
  height: 35px;
  border-style: solid;
  border-width: 1px;
  border-color: #e4e4e4;
  border-radius: 4px;
  font-size: 14px;
  margin: 5px;
}
.is-actived {
  background-color: rgb(82, 157, 255);
  color: white;
}

.iconStyle {
  font-size: 25px;
  color: #999999;
}
</style>
