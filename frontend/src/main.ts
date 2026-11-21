import { createApp } from 'vue'
import {
  Alert, Button, Dialog, Form, FormItem, Input, InputNumber, Loading, Select, Tag, Textarea,
} from 'tdesign-vue-next'
import 'tdesign-vue-next/es/style/index.css'
import App from './App.vue'
import './styles.css'

const app = createApp(App)
app.use(Alert)
app.use(Button)
app.use(Dialog)
app.use(Form)
app.use(FormItem)
app.use(Input)
app.use(InputNumber)
app.use(Loading)
app.use(Select)
app.use(Tag)
app.use(Textarea)
// TDesign forwards the id attribute to the wrapper. The form label needs the native control.
app.directive<HTMLElement, string>('field-id', (element, { value }) => {
  const control = element.querySelector('input:not([type="hidden"]), textarea')
  if (control) control.id = value
})
app.mount('#app')
