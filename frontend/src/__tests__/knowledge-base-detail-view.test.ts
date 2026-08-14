// KnowledgeBaseDetailView（库详情）视图测试：页头、过滤、分页、空态、新建笔记/上传归属当前库。
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import KnowledgeBaseDetailView from '../views/KnowledgeBaseDetailView.vue'
import * as kbApi from '../api/knowledge-bases'
import * as documentsApi from '../api/documents'
import * as tagsApi from '../api/tags'
import * as filesApi from '../api/files'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'
import type { DocumentPageResponse, KnowledgeDocumentResponse } from '../api/types/document'

const kb: KnowledgeBaseResponse = {
  id: '2',
  name: 'Java',
  description: 'Java notes',
  enabled: true,
  documentCount: 3,
  rowVersion: 0,
  createdAt: '2026-08-09T00:00:00Z',
  updatedAt: '2026-08-09T00:00:00Z',
}

const summaryItem = {
  id: '1',
  sourceType: 'MANUAL_NOTE' as const,
  title: 'My Note',
  contentVersion: 1,
  indexStatus: 'INDEXED',
  knowledgeBaseId: '2',
  tags: ['java'],
  rowVersion: 0,
  createdAt: '2026-08-09T00:00:00Z',
  updatedAt: '2026-08-09T00:00:00Z',
}

const page: DocumentPageResponse = {
  items: [summaryItem],
  page: 1,
  size: 20,
  total: 1,
}

/** script setup 内部绑定经 VTU 暴露在 vm 上，这里显式声明供测试直接修改过滤条件。 */
type DetailVm = {
  sourceType: string
  tag: string
  uploadFileRaw: File | null
}

async function mountView(): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/knowledge-bases/:id', component: { template: '<div />' } },
      { path: '/documents/:id', component: { template: '<div />' } },
    ],
  })
  await router.push('/knowledge-bases/2')
  await router.isReady()
  return mount(KnowledgeBaseDetailView, { global: { plugins: [ElementPlus, router] } })
}

describe('KnowledgeBaseDetailView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  function stubBaseApis() {
    vi.spyOn(kbApi, 'getKnowledgeBase').mockResolvedValue(kb)
    vi.spyOn(tagsApi, 'listTags').mockResolvedValue([
      { id: '1', name: 'java' },
      { id: '2', name: 'spring' },
    ])
  }

  it('renders the header and loads documents of the current knowledge base', async () => {
    const listMock = vi.spyOn(documentsApi, 'listDocuments').mockResolvedValue(page)
    stubBaseApis()
    const wrapper = await mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Java')
    expect(wrapper.text()).toContain('已启用')
    expect(wrapper.text()).toContain('My Note')
    expect(listMock).toHaveBeenCalledWith({ knowledgeBaseId: '2', page: 1, size: 20 })
  })

  it('reloads from page 1 when a filter changes', async () => {
    const listMock = vi.spyOn(documentsApi, 'listDocuments').mockResolvedValue(page)
    stubBaseApis()
    const wrapper = await mountView()
    await flushPromises()
    const vm = wrapper.vm as unknown as DetailVm

    vm.sourceType = 'UPLOAD_FILE'
    await nextTick()
    await flushPromises()
    expect(listMock).toHaveBeenLastCalledWith({
      knowledgeBaseId: '2',
      sourceType: 'UPLOAD_FILE',
      page: 1,
      size: 20,
    })

    vm.tag = 'java'
    await nextTick()
    await flushPromises()
    expect(listMock).toHaveBeenLastCalledWith({
      knowledgeBaseId: '2',
      sourceType: 'UPLOAD_FILE',
      tag: 'java',
      page: 1,
      size: 20,
    })
  })

  it('shows an empty state with a create-note action when no documents', async () => {
    vi.spyOn(documentsApi, 'listDocuments').mockResolvedValue({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    })
    stubBaseApis()
    const wrapper = await mountView()
    await flushPromises()

    expect(wrapper.find('.kf-empty--wide').exists()).toBe(true)
    expect(wrapper.text()).toContain('这个知识库还没有文档')
  })

  it('creates a note fixed to the current knowledge base and navigates to it', async () => {
    const created: KnowledgeDocumentResponse = { ...summaryItem, id: '9', content: '# hello' }
    const createMock = vi.spyOn(documentsApi, 'createDocument').mockResolvedValue(created)
    vi.spyOn(documentsApi, 'listDocuments').mockResolvedValue(page)
    stubBaseApis()
    const wrapper = await mountView()
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '新建笔记')!
      .trigger('click')
    await nextTick()

    const textareas = wrapper.findAll('textarea')
    await textareas[textareas.length - 1].setValue('# hello')
    const inputs = wrapper.findAll('input')
    await inputs[inputs.length - 1].setValue('java, ai')

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '创建')!
      .trigger('click')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({
        content: '# hello',
        knowledgeBaseId: '2',
        tags: ['java', 'ai'],
      }),
    )
  })

  it('uploads a file to the current knowledge base and navigates to the item', async () => {
    const uploadMock = vi.spyOn(filesApi, 'uploadFile').mockResolvedValue({
      duplicate: false,
      item: { id: '9', title: 'a.md', sourceType: 'UPLOAD_FILE', indexStatus: 'PENDING' },
    })
    vi.spyOn(documentsApi, 'listDocuments').mockResolvedValue(page)
    stubBaseApis()
    const wrapper = await mountView()
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '上传文件')!
      .trigger('click')
    await nextTick()
    const vm = wrapper.vm as unknown as DetailVm
    vm.uploadFileRaw = new File(['# hello'], 'a.md', { type: 'text/markdown' })
    await nextTick()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '上传')!
      .trigger('click')
    await flushPromises()

    expect(uploadMock).toHaveBeenCalledWith(expect.any(File), '2')
  })

  it('aggregates two-phase statuses with error codes in the status column (P4)', async () => {
    const mixedPage: DocumentPageResponse = {
      items: [
        {
          ...summaryItem,
          id: '1',
          title: '解析失败文件',
          sourceType: 'UPLOAD_FILE',
          indexStatus: 'PENDING',
          parseStatus: 'FAILED',
          parseErrorCode: 'DOCUMENT_PARSE_FAILED',
          parseErrorMessage: '文件编码不支持',
        },
        {
          ...summaryItem,
          id: '2',
          title: '解析中文件',
          sourceType: 'UPLOAD_FILE',
          indexStatus: 'PENDING',
          parseStatus: 'PENDING',
        },
        {
          ...summaryItem,
          id: '3',
          title: '索引失败笔记',
          indexStatus: 'FAILED',
          indexErrorCode: 'EMBEDDING_UNAVAILABLE',
          indexErrorMessage: 'Embedding 服务不可用',
        },
        { ...summaryItem, id: '4', title: '已索引笔记', indexStatus: 'INDEXED' },
      ],
      page: 1,
      size: 20,
      total: 4,
    }
    vi.spyOn(documentsApi, 'listDocuments').mockResolvedValue(mixedPage)
    stubBaseApis()
    const wrapper = await mountView()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('解析失败')
    expect(text).toContain('解析中')
    expect(text).toContain('索引失败')
    expect(text).toContain('已索引')

    const tooltips = wrapper.findAllComponents({ name: 'ElTooltip' })
    const contents = tooltips.map((t) => t.props('content'))
    expect(contents).toContain('DOCUMENT_PARSE_FAILED：文件编码不支持')
    expect(contents).toContain('EMBEDDING_UNAVAILABLE：Embedding 服务不可用')
  })
})
