import { createApp } from 'vue'
import {
  Alert, Button, Dialog, Input, InputNumber, Loading, Select, Tag, Textarea,
} from 'tdesign-vue-next'
import 'tdesign-vue-next/es/style/index.css'
import App from './App.vue'
import './styles.css'

const app = createApp(App)
app.use(Alert)
app.use(Button)
app.use(Dialog)
app.use(Input)
app.use(InputNumber)
app.use(Loading)
app.use(Select)
app.use(Tag)
app.use(Textarea)
app.mount('#app')
