package cn.diveplan.importer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 ApiKey 安全存到 EncryptedSharedPreferences。
 *
 * 选 EncryptedSharedPreferences 而非裸 SharedPreferences 的原因：
 *   - AES-256 GCM 自动加密，root 设备 / adb backup 都看不到明文
 *   - Android KeyStore 托管的主密钥跟设备绑定，APK 拷到别的手机不能解
 *
 * 暴露 [apiKey] StateFlow 让 UI 层观察绑定状态 —— 切换 BindScreen ↔ Home 用这个判断。
 *
 * 不在这里塞业务逻辑（如 consume / 刷新），那些在 [cn.diveplan.importer.data.BindRepository]。
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, null))
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _prefix = MutableStateFlow(prefs.getString(KEY_PREFIX, null))
    val prefix: StateFlow<String?> = _prefix.asStateFlow()

    fun get(): String? = _apiKey.value

    fun isBound(): Boolean = !_apiKey.value.isNullOrBlank()

    fun save(apiKey: String, prefix: String, expiresAt: String?) {
        prefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_PREFIX, prefix)
            .putString(KEY_EXPIRES_AT, expiresAt)
            .apply()
        _apiKey.value = apiKey
        _prefix.value = prefix
    }

    /** 用户手动解绑 / 切换账号时调；清空所有持久化字段并立即同步给观察者。 */
    fun clear() {
        prefs.edit().clear().apply()
        _apiKey.value = null
        _prefix.value = null
    }

    companion object {
        private const val FILE_NAME = "dpi_secure_prefs"
        private const val KEY_API_KEY    = "api_key"
        private const val KEY_PREFIX     = "api_key_prefix"
        private const val KEY_EXPIRES_AT = "api_key_expires_at"
    }
}
