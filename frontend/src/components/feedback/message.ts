import { readonly, ref } from 'vue';

export type MessageType = 'success' | 'error' | 'warning' | 'info';

export interface MessageItem {
  id: number;
  type: MessageType;
  text: string;
  duration: number;
}

const messageItems = ref<MessageItem[]>([]);
let nextMessageId = 1;
const timers = new Map<number, ReturnType<typeof setTimeout>>();

function close(id: number) {
  const timer = timers.get(id);
  if (timer) clearTimeout(timer);
  timers.delete(id);
  messageItems.value = messageItems.value.filter((item) => item.id !== id);
}

function open(type: MessageType, text: string, duration = 3000) {
  const id = nextMessageId++;
  messageItems.value.push({ id, type, text, duration });
  if (duration > 0) timers.set(id, setTimeout(() => close(id), duration));
  return id;
}

export const messages = readonly(messageItems);

export const message = {
  success: (text: string, duration?: number) => open('success', text, duration),
  error: (text: string, duration?: number) => open('error', text, duration),
  warning: (text: string, duration?: number) => open('warning', text, duration),
  info: (text: string, duration?: number) => open('info', text, duration),
  close
};

export function clearMessages() {
  for (const id of timers.keys()) close(id);
  messageItems.value = [];
}
