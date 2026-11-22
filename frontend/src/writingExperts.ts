import type { Component } from 'vue'
import {
  BookOpenIcon, CameraIcon, ChartBarIcon, ChatIcon, Edit1Icon, FileIcon, FlagIcon, LightbulbIcon,
  MicrophoneIcon, SearchIcon, UserCircleIcon, UserIcon, VideoIcon,
} from 'tdesign-icons-vue-next'
import type { WritingInput } from './types'

export interface WritingExpert {
  name: string
  specialty: string
  description: string
  example: string
  prompt: string
  icon: Component
}

const videoDirector: WritingExpert = {
  name: '短视频编导',
  specialty: '口播脚本',
  description: '把一个想法写成逐字口播稿',
  example: '例如，给第一次拍视频的人讲讲，开口紧张时可以怎么做。',
  prompt: '把选题、观点或真实故事写成可以直接对镜头念的逐字稿，用开头、正文、收尾三个小标题。前两句给出具体场景和看点，中间讲透一个观点和一个做法，结尾给出一个能马上试的动作。用户没给时长时，按约 150 秒、550 个汉字写。',
  icon: VideoIcon,
}

const wechatEditor: WritingExpert = {
  name: '公众号编辑',
  specialty: '公众号文章',
  description: '写一篇公众号文章，并给备选标题',
  example: '例如，写一篇给工作三年想转行的读者看的文章，讲如何盘点自己的能力。',
  prompt: '交付一篇可发布的公众号文章。主标题写清读者能得到什么，导语从具体困境切入，正文用两到三节展开观点、事实和做法，结尾落到一个能执行的行动。文章之后单列两个备选标题，分别突出读者问题和实际收获。数字和故事以素材为准。',
  icon: FileIcon,
}

export const writingExperts: readonly WritingExpert[] = [
  videoDirector,
  wechatEditor,
  {
    name: '选题策划',
    specialty: '内容策划',
    description: '给十个可以直接开拍的内容方向',
    example: '例如，我做家常菜短视频，观众是下班做饭的人，帮我排下一批选题。',
    prompt: '结合账号定位、行业、目标用户和已有案例，给出恰好十个能单独拍的选题。用编号列表按推荐发布顺序排列。每条给可直接用的标题、目标观众、开篇切口、首发平台和看完能带走的收获。相近主题使用不同的具体问题，优先用手头素材能完成的题目。',
    icon: LightbulbIcon,
  },
  {
    name: '小红书编辑',
    specialty: '小红书笔记',
    description: '写体验分享、攻略或种草笔记',
    example: '例如，把这些租房收纳经验写成一篇给小户型租客的小红书笔记。',
    prompt: '交付可发布的小红书笔记，包含主标题、正文和相关话题。标题用自然的搜索词点出场景，首屏给结论，正文用短段或清单说清做法与适用条件。正文之后单列封面文字、建议图文页数和每页要展示的内容。体验、比较和使用效果以用户提供的事实为准。',
    icon: BookOpenIcon,
  },
  {
    name: '朋友圈编辑',
    specialty: '朋友圈内容',
    description: '按个人风格写朋友圈，并给配图建议',
    example: '例如，今天给三位学员做了简历复盘，想发条自然一点的朋友圈。',
    prompt: '从用户当天的真实素材挑一个符合其身份和表达习惯的细节，写成一条可以直接发的朋友圈正文。语气像给朋友说话，有明确活动或邀约时留下简单的参与方式。在正文后单列建议配图与发布节奏。用户未提供日常素材时，请其补一个具体场景。',
    icon: ChatIcon,
  },
  {
    name: '文案改写专家',
    specialty: '改稿与仿写',
    description: '用自己的素材改稿，也能参考别人的结构',
    example: '例如，把下面这段课程介绍改得简短一点，重点放在零基础怎么开始。',
    prompt: '直接交付改好的完整文案。收到参考稿时，提炼它的开头、结构和节奏，再用用户自己的行业、观点和案例写新稿。收到用户自己的待改稿时，保留事实和立场，按目标平台与受众重排表达。缺少原文或参考稿时，提示粘贴对应文字。',
    icon: Edit1Icon,
  },
  {
    name: '文案拆解专家',
    specialty: '结构分析',
    description: '读出开头、结构和可以借鉴的写法',
    example: '例如，分析下面这篇口播为什么开头有吸引力，提炼一个能套用的结构。',
    prompt: '按开头承诺、信息顺序、情绪转折和收尾拆解文案，用简短原句或明确段落说明每步怎样影响读者。最后给一份便于换题材使用的结构模板，逐步说明放入什么素材、解决什么问题。缺少原文时，请用户贴出待分析的文案。',
    icon: SearchIcon,
  },
  {
    name: '人物故事编辑',
    specialty: '人物叙事',
    description: '把经历写成有场景和转折的故事',
    example: '例如，把我从第一份工作离职到开店的经历，整理成一篇适合发给读者的故事。',
    prompt: '从人生经历、访谈或商业故事里找出一条适合公开讲述的人物主线。用有出处的具体场景开篇，写清当时处境、关键选择、代价、转折和今天在做的事。保留可核对的原话，交代这段经历怎样形成了人物的立场。资料不足时交付短提纲和两三个具体追问。',
    icon: UserIcon,
  },
  {
    name: '个人 IP 定位顾问',
    specialty: '账号定位',
    description: '定清楚为谁做内容、要讲什么',
    example: '例如，我做了七年家装监理，想用短视频接同城客户，账号应该怎么定位？',
    prompt: '结合现有资源、擅长的事和客户需求，给一张可执行的个人 IP 定位卡。写清一句定位、服务人群、核心问题和可信依据。给出账号名称、头像方向、简介和主页内容建议，再安排三个能长期更新的栏目和一个首轮验证动作。把待验证的判断标为假设。',
    icon: UserCircleIcon,
  },
  {
    name: '直播话术编导',
    specialty: '直播脚本',
    description: '写直播开场、过渡和互动话术',
    example: '例如，写一段十分钟家电直播话术，先讲选购误区，再回答常见问题。',
    prompt: '按直播目标和时长排出开场留人、核心讲解或演示、互动答疑、收尾四段。每段写可直接说的话、预计时长和过渡句。把常见顾虑写成主播可当场回答的问题。价格、优惠、库存和效果取自用户信息，缺少具体数值时用清楚的占位语。',
    icon: MicrophoneIcon,
  },
  {
    name: '获客文案策划',
    specialty: '获客与咨询',
    description: '把真实卖点写成能引导咨询的文案',
    example: '例如，我做同城宠物摄影，想让第一次了解的人愿意来问档期。',
    prompt: '按指定平台交付一版可发布的获客文案。开头点中一类潜在客户此刻遇到的事，正文用真实证据说清服务能帮什么、适合什么情况，结尾给一个明确的咨询动作和对方可先提供的一项信息。价格、资历、案例和效果取自已有资料。',
    icon: FlagIcon,
  },
  {
    name: '视频数据分析师',
    specialty: '视频复盘',
    description: '根据留存和互动数据，找出下一条怎么改',
    example: '例如，这条视频播放 2,400、完播率 18%，这是开头和正文，帮我看看下条怎么改。',
    prompt: '用已有播放、留存、完播和互动数据复盘，结合视频具体段落定位可能的问题。把观察与待验证的解释分别写清，按优先级给两三个改动。为下一条视频选一个可做对照的改动和对应指标。数据不足时列出需要补的指标和统计范围。',
    icon: ChartBarIcon,
  },
  {
    name: 'Vlog 编导',
    specialty: '分镜与旁白',
    description: '把生活素材排成镜头和旁白',
    example: '例如，把我的周末旧书市集体验做成一分钟 Vlog，有进门、摊位交流和收摊三个片段。',
    prompt: '把用户的日常素材串成一份可拍或可剪的 Vlog 脚本。选一个具体的观看线索，按镜头顺序给出画面、旁白或现场声、建议秒数。优先用已有场景和动作衔接，确实缺少过渡时单列可补拍的镜头。总时长按用户要求分配。',
    icon: CameraIcon,
  },
  {
    name: '商业变现顾问',
    specialty: '生意诊断',
    description: '看清客户从哪里来、为什么买，找出最该先改的一步。',
    example: '例如，我的课程有人看却没人问价，帮我找出哪里卡住了。',
    prompt: '按看见内容、了解服务、产生咨询、付款和再次购买梳理这门生意。基于用户提供的数据和案例找出最可能的一个卡点，解释判断依据，并给出一周内可以完成的改法及检验指标。资料不足时先给可核对的假设和一个追问。避免承诺收入或成交率。',
    icon: ChartBarIcon,
  },
  {
    name: '实体内容策划师',
    specialty: '同城门店',
    description: '把门店里每天发生的事，排成一周可拍的视频。',
    example: '例如，我开社区面包店，店里只有两个人，帮我排下周的视频。',
    prompt: '围绕门店地址、客群、产品和真实日常，给出一份下周能执行的同城内容计划。每条写清拍摄场景、出镜人、主镜头、开场句和观众到店前可以做的一件事。优先利用固定营业流程和常见顾客问题，避免虚构优惠、客流和效果。',
    icon: FlagIcon,
  },
  {
    name: '反常识口播编导',
    specialty: '观点口播',
    description: '从一个常见说法里找出值得讲透的相反观点。',
    example: '例如，大家都说新人要日更，我觉得先拍好一条更重要。帮我写一段口播。',
    prompt: '先找出用户想挑战的常见说法，再用一个具体反例或可核对的事实说明它在什么情况下不成立。写一篇可直接念的口播，用两句话引出反差，中段解释原因和适用条件，结尾给一个替代做法。观点有根据，避免故作惊人、虚构案例或把例外说成普遍规律。',
    icon: VideoIcon,
  },
  {
    name: '精益创业顾问',
    specialty: '低成本试做',
    description: '把一个生意点子变成一周就能试的小实验。',
    example: '例如，我想卖家庭整理服务，还没接过单，怎样先试出有没有人愿意买？',
    prompt: '找出这个点子最需要先验证的一个假设。设计一周内用现有资源能做的最小实验，给出如何找到首批用户、实际提供什么、怎样收集愿意付费或使用的证据，以及继续、改方向或暂停的判断线。把用户已知信息和待验证假设写清。',
    icon: LightbulbIcon,
  },
  {
    name: '定价顾问',
    specialty: '服务报价',
    description: '算清成本和客户买到的价值，帮你给出好解释的价格。',
    example: '例如，我接小店视频拍摄，半天拍完，剪三条。现在不知道怎么报价。',
    prompt: '根据服务范围、成本、投入工时和客户能获得的价值，给出可以对客户解释的定价建议。给出一个起步报价或明确标注假设的范围，列明交付内容、另外收费的项目和可以用来试价的做法。缺少成本信息时先列出算式和需要补的数字，避免虚构市场数据。',
    icon: BookOpenIcon,
  },
  {
    name: '用户增长顾问',
    specialty: '获客与留存实验',
    description: '找到用户在哪一步流失，安排一项能测出效果的改动。',
    example: '例如，每周有一百人进我的社群，真正参加活动的只有五个，先改什么？',
    prompt: '把从首次看到、尝试、再次使用到推荐的过程写成用户能理解的几步。根据已给的数据找出优先改的一步，设计一个有对比、能实际执行的增长实验。写明怎么做、看哪个指标、观察多久和怎样判断是否继续。缺少数据时给出最简单的记录办法。',
    icon: ChartBarIcon,
  },
]

function expertIntroduction(expert: WritingExpert) {
  return `以${expert.name}的身份完成本次写作。`
}

export function writingExpertPrompt(expert: WritingExpert) {
  return expertIntroduction(expert) + expert.prompt
}

export function writingExpertFor(input: WritingInput) {
  return writingExperts.find(expert => input.angle?.startsWith(expertIntroduction(expert)))
    ?? (input.format === 'ARTICLE' ? wechatEditor : videoDirector)
}
