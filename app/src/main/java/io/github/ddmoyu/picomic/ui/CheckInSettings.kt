package io.github.ddmoyu.picomic.ui

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddmoyu.picomic.data.Source

@Composable fun CheckInSettings(source: Source, vm: AppViewModel) {
    val states by vm.checkins.state.collectAsStateWithLifecycle()
    val status = states[source]
    SettingRow(if (source == Source.PICACG) "立即打卡" else "测试签到", if (status?.busy == true) "正在确认平台状态" else status?.message ?: "按账号分别记录；失败不会显示成功", Glyph.Check,
        onClick = { if (status?.busy != true) vm.checkins.run(source) })
}
