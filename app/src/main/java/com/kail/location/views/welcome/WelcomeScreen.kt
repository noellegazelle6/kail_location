package com.kail.location.views.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kail.location.R

/**
 * 欢迎屏幕 UI
 * 包含全屏背景图、进入按钮以及协议勾选区域。
 *
 * @param onStartClick 点击进入应用的回调
 * @param onAgreementClick 点击用户协议链接的回调
 * @param onPrivacyClick 点击隐私政策链接的回调
 * @param isChecked 协议勾选框的选中状态
 * @param onCheckedChange 协议勾选框状态变更回调
 */
@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit,
    onAgreementClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.welcome),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .width(200.dp)
            ) {
                Text(text = stringResource(id = R.string.welcome_btn_txt))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = Color.White
                    )
                )

                val agreementText = stringResource(id = R.string.app_agreement_privacy)
                val agreementPart = "《用户协议》"
                val privacyPart = "《隐私政策》"

                val annotatedString = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color.White)) {
                        append("已阅读")
                    }
                    
                    withLink(LinkAnnotation.Clickable("agreement") { onAgreementClick() }) {
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(agreementPart)
                        }
                    }

                    withStyle(style = SpanStyle(color = Color.White)) {
                        append("和")
                    }

                    withLink(LinkAnnotation.Clickable("privacy") { onPrivacyClick() }) {
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(privacyPart)
                        }
                    }
                }

                Text(text = annotatedString)
            }
        }
    }
}

/**
 * 协议/隐私政策弹窗
 * 展示协议的具体文本内容，提供同意与不同意按钮。
 *
 * @param title 弹窗标题
 * @param content 协议文本内容
 * @param onDismiss 点击不同意或取消的回调
 * @param onAgree 点击同意的回调
 */
@Composable
fun AgreementDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
    onAgree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { 
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = content)
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(text = stringResource(id = R.string.app_btn_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.app_btn_disagree))
            }
        }
    )
}
