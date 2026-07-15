package com.fox.music.feature.auth.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.theme.FoxDisplayFont
import com.fox.music.core.ui.theme.FoxSemanticColors
import com.fox.music.core.ui.theme.FoxShapes
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.auth.viewmodel.AuthEffect
import com.fox.music.feature.auth.viewmodel.AuthIntent
import com.fox.music.feature.auth.viewmodel.AuthState
import com.fox.music.feature.auth.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

const val LOGIN_ROUTE = "login"

private enum class AuthMode {
    Login,
    Register,
    Reset,
}

private fun AuthState.toMode(): AuthMode = when {
    isResetMode -> AuthMode.Reset
    isLoginMode -> AuthMode.Login
    else -> AuthMode.Register
}

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    val passwordError by remember {
        derivedStateOf { state.password.length in 1 until 6 }
    }
    val emailHasErrors by remember {
        derivedStateOf {
            state.email.isNotEmpty() &&
                !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()
        }
    }
    val verifyError by remember {
        derivedStateOf {
            state.verifyCode.isNotEmpty() && state.verifyCode.length != 6
        }
    }
    val usernameBlank by remember {
        derivedStateOf { state.username.isEmpty() }
    }

    val canSubmit = !state.isLoading &&
        ((!emailHasErrors && state.email.isNotEmpty()) || state.isLoginMode) &&
        ((!verifyError && state.verifyCode.isNotEmpty()) || !state.isResetMode) &&
        (!passwordError && state.password.isNotEmpty()) &&
        (!usernameBlank || state.isResetMode)

    LaunchedEffect(state.waitingSecond) {
        if (state.waitingSecond > 0) {
            delay(1000)
            viewModel.sendIntent(AuthIntent.UpdateWaiting(state.waitingSecond - 1))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect {
            when (it) {
                is AuthEffect.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    BackHandler(onBack = onNavigateBack)

    val mode = state.toMode()
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        AuthAmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            AuthTopBar(onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FoxSpacing.pagePadding)
                    .padding(bottom = FoxSpacing.sectionSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 8 }) togetherWith
                            (fadeOut() + slideOutVertically { -it / 10 })
                    },
                    label = "auth_header",
                ) { current ->
                    AuthHeader(mode = current)
                }

                Spacer(modifier = Modifier.height(FoxSpacing.sectionSpacing))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = scheme.outline.copy(alpha = 0.28f),
                            shape = FoxShapes.large,
                        ),
                    shape = FoxShapes.large,
                    color = scheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = FoxSpacing.cardPadding + 4.dp,
                                vertical = FoxSpacing.cardPadding + 6.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!state.isLoginMode || state.isResetMode) {
                            AuthTextField(
                                value = state.email,
                                onValueChange = { viewModel.sendIntent(AuthIntent.EmailChange(it)) },
                                label = "邮箱",
                                leadingIcon = Icons.Outlined.Email,
                                isError = emailHasErrors,
                                supportingText = if (emailHasErrors) "邮箱格式不正确" else null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                ),
                            )
                        }

                        if (state.isResetMode) {
                            AuthVerifyRow(
                                code = state.verifyCode,
                                waitingSecond = state.waitingSecond,
                                enabled = !emailHasErrors && state.email.isNotEmpty() && state.waitingSecond <= 0,
                                isError = verifyError,
                                onCodeChange = { viewModel.sendIntent(AuthIntent.VerifyChange(it)) },
                                onSend = { viewModel.sendIntent(AuthIntent.SendVerify) },
                            )
                        }

                        if (!state.isResetMode) {
                            AuthTextField(
                                value = state.username,
                                onValueChange = { viewModel.sendIntent(AuthIntent.UsernameChange(it)) },
                                label = if (state.isLoginMode) "用户名或邮箱" else "用户名",
                                leadingIcon = Icons.Outlined.Person,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                ),
                            )
                        }

                        AuthTextField(
                            value = state.password,
                            onValueChange = { viewModel.sendIntent(AuthIntent.PasswordChange(it)) },
                            label = if (state.isResetMode) "新密码" else "密码",
                            leadingIcon = Icons.Outlined.Lock,
                            isError = passwordError,
                            supportingText = if (passwordError) "密码至少 6 位" else null,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingContent = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                        tint = scheme.onSurfaceVariant,
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (canSubmit) viewModel.sendIntent(AuthIntent.Submit)
                                },
                            ),
                        )

                        state.error?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FoxSpacing.sectionSpacing))

                Button(
                    onClick = { viewModel.sendIntent(AuthIntent.Submit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = canSubmit,
                    shape = FoxShapes.medium,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Text(
                        text = when (mode) {
                            AuthMode.Reset -> "确认修改"
                            AuthMode.Login -> "登录"
                            AuthMode.Register -> "创建账户"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(FoxSpacing.itemSpacing))

                AuthModeLinks(
                    mode = mode,
                    onToggleRegister = { viewModel.sendIntent(AuthIntent.ToggleToRegisterMode) },
                    onToggleReset = { viewModel.sendIntent(AuthIntent.ToggleToResetMode) },
                )
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.scrim.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = FoxShapes.medium,
                    color = scheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(28.dp)
                            .size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = scheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthAmbientBackground() {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.14f),
                            scheme.background,
                            scheme.background,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 48.dp, y = 36.dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(FoxSemanticColors.amberGlow.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-72).dp, y = (-40).dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(scheme.secondary.copy(alpha = 0.08f)),
        )
    }
}

@Composable
private fun AuthTopBar(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun AuthHeader(mode: AuthMode) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "FoxMusic",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = FoxDisplayFont,
                fontWeight = FontWeight.Bold,
            ),
            color = scheme.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = when (mode) {
                AuthMode.Login -> "欢迎回来"
                AuthMode.Register -> "创建账户"
                AuthMode.Reset -> "重置密码"
            },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FoxDisplayFont,
                fontWeight = FontWeight.SemiBold,
            ),
            color = scheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when (mode) {
                AuthMode.Login -> "登录以同步歌单、收藏与听歌记录"
                AuthMode.Register -> "加入夜场，封面即舞台"
                AuthMode.Reset -> "验证邮箱后设置新密码"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingContent: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (isError) scheme.error else scheme.onSurfaceVariant,
            )
        },
        trailingIcon = trailingContent,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        shape = FoxShapes.medium,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline.copy(alpha = 0.45f),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = scheme.primary,
        ),
    )
}

@Composable
private fun AuthVerifyRow(
    code: String,
    waitingSecond: Int,
    enabled: Boolean,
    isError: Boolean,
    onCodeChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) onCodeChange(it.filter(Char::isDigit)) },
            modifier = Modifier.weight(1f),
            label = { Text("验证码") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Password,
                    contentDescription = null,
                    tint = if (isError) scheme.error else scheme.onSurfaceVariant,
                )
            },
            isError = isError,
            supportingText = if (isError) {
                { Text("请输入 6 位验证码") }
            } else {
                null
            },
            singleLine = true,
            shape = FoxShapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outline.copy(alpha = 0.45f),
            ),
        )
        TextButton(
            onClick = onSend,
            enabled = enabled,
            modifier = Modifier
                .padding(top = 8.dp)
                .height(56.dp),
            shape = FoxShapes.small,
        ) {
            Text(
                text = if (waitingSecond > 0) "${waitingSecond}s" else "发送",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
private fun AuthModeLinks(
    mode: AuthMode,
    onToggleRegister: () -> Unit,
    onToggleReset: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (mode) {
            AuthMode.Login -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "还没有账户？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onToggleRegister) {
                        Text(
                            text = "去注册",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
                TextButton(onClick = onToggleReset) {
                    Text(
                        text = "忘记密码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            AuthMode.Register -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "已有账户？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onToggleRegister) {
                        Text(
                            text = "返回登录",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }

            AuthMode.Reset -> {
                TextButton(onClick = onToggleReset) {
                    Text(
                        text = "返回登录",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}
