package com.crinfo.app.ui.components

import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 全应用统一的「Q 弹」滚动修饰。
 *
 * 来源是 Miuix 0.9.4 自带的 `overScrollVertical()`：越界位移由 spring 弹回、松手把惯性交给
 * fling，配 `scrollEndHaptic()` 在滚到边界时给一次轻触感。参考项目 Melox 的每个列表都按这个
 * 口径挂（并把原生 overscrollEffect 关掉，避免与系统 stretch 叠加出两层回弹）。
 * 本项目用的是同一版本 Miuix，无需自定义 spring 参数。
 *
 * 用法：挂在滚动容器 modifier 链上、`.verticalScroll(...)` 之前：
 * ```
 * Column(Modifier.fillMaxSize().appBounceScroll().verticalScroll(rememberScrollState()))
 * ```
 * 只给「内容短、能滚到边界」的页面用；下拉刷新容器、地图页与不可滚动页不要挂。
 */
fun Modifier.appBounceScroll(): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
