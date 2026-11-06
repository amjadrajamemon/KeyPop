package com.aetherkeys.keyboard

import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

data class KeyPopAutofillFields(val user:AutofillId?,val pass:AutofillId?,val domain:String?) {
    val hasFields get() = user!=null || pass!=null
}
private fun discoverLoginFields(structure:AssistStructure):KeyPopAutofillFields {
    var username:AutofillId?=null
    var password:AutofillId?=null
    val domains=mutableSetOf<String>()
    fun walk(node:AssistStructure.ViewNode) {
        node.webDomain?.let { val v=VaultTarget.canonical(it);if(v.isNotBlank()) domains.add(v) }
        val hints=node.autofillHints?.joinToString(" ")?.lowercase().orEmpty()
        val clue=(node.idEntry.orEmpty()+" "+node.hint.orEmpty()).lowercase()
        val variation=node.inputType and InputType.TYPE_MASK_VARIATION
        val passwordType=variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        if(node.autofillType==View.AUTOFILL_TYPE_TEXT) {
            if(password==null && (passwordType||hints.contains("password")||clue.contains("password")))
                password=node.autofillId
            else if(username==null && (hints.contains("username")||hints.contains("email")
                        ||clue.contains("username")||clue.contains("email")||clue.contains("login")))
                username=node.autofillId
        }
        for(i in 0 until node.childCount)walk(node.getChildAt(i))
    }
    for(i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
    return KeyPopAutofillFields(username,password,if(domains.size==1)domains.first() else if(domains.isEmpty())null else "")
}

/** Autofill remains OS-mediated, requires exact origin and authentication for every selected login. */
class KeyPopAutofillService:AutofillService() {
    override fun onFillRequest(request:FillRequest,signal:CancellationSignal,callback:FillCallback) {
        if(signal.isCanceled){callback.onSuccess(null);return}
        try {
            val structure=request.fillContexts.lastOrNull()?.structure ?:run{callback.onSuccess(null);return}
            val fields=discoverLoginFields(structure)
            val packageName=structure.activityComponent?.packageName.orEmpty()
            val browsers=setOf("com.android.chrome","org.mozilla.firefox","com.microsoft.emmx",
                "com.brave.browser","com.sec.android.app.sbrowser")
            // Never let an arbitrary application claim a website origin via ViewNode.webDomain.
            // Browsers require an explicit website hostname; other apps match only their package.
            val target=if(packageName in browsers) fields.domain.orEmpty() else packageName
            if(!fields.hasFields||target.isBlank()){callback.onSuccess(null);return}
            val logins=KeyPopLoginVault(this).find(target)
            if(logins.isEmpty()){callback.onSuccess(null);return}
            val response=FillResponse.Builder()
            logins.take(10).forEach { login ->
                val auth=Intent(this,KeyPopAutofillUnlockActivity::class.java).apply {
                    putExtra("accountId",login.id)
                    putExtra("target",target)
                    fields.user?.let {putExtra("userId",it)}
                    fields.pass?.let {putExtra("passId",it)}
                    data=Uri.parse("keypop://vault/"+Uri.encode(login.id))
                }
                val pending=PendingIntent.getActivity(this,login.id.hashCode(),auth,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                val presentation=RemoteViews(getPackageName(),android.R.layout.simple_list_item_1).apply {
                    setTextViewText(android.R.id.text1,"KeyPop · "+login.label.ifBlank{login.username})
                }
                val builder=Dataset.Builder()
                fields.user?.let{builder.setValue(it,null,presentation)}
                fields.pass?.let{builder.setValue(it,null,presentation)}
                response.addDataset(builder.setAuthentication(pending.intentSender).build())
            }
            callback.onSuccess(response.build())
        }catch(_:Exception){callback.onSuccess(null)}
    }
    override fun onSaveRequest(request:SaveRequest,callback:SaveCallback) {
        // Never scrape submitted passwords. Users explicitly add logins in the vault.
        callback.onSuccess()
    }
}

/** Authentication occurs before the credentials are placed in the Android Autofill dataset. */
class KeyPopAutofillUnlockActivity:Activity() {
    private val req=90
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val lock=getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if(!lock.isDeviceSecure){finish();return}
        @Suppress("DEPRECATION")
        val prompt=lock.createConfirmDeviceCredentialIntent("KeyPop Autofill","Unlock your account")
        if(prompt==null){finish();return}
        @Suppress("DEPRECATION")
        startActivityForResult(prompt,req)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==req && resultCode==RESULT_OK){
            try {
                val account=KeyPopLoginVault(this).get(intent.getStringExtra("accountId").orEmpty())
                val target=intent.getStringExtra("target").orEmpty()
                if(account!=null && VaultTarget.matches(account.target,target)) {
                    @Suppress("DEPRECATION")
                    val user=intent.getParcelableExtra<AutofillId>("userId")
                    @Suppress("DEPRECATION")
                    val pass=intent.getParcelableExtra<AutofillId>("passId")
                    val presentation=RemoteViews(packageName,android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1,"KeyPop login")
                    }
                    val builder=Dataset.Builder()
                    user?.let{builder.setValue(it,AutofillValue.forText(account.username),presentation)}
                    pass?.let{builder.setValue(it,AutofillValue.forText(account.password),presentation)}
                    if(user!=null||pass!=null) setResult(RESULT_OK,Intent()
                        .putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT,builder.build()))
                }
            }catch(_:Exception){}
        }
        finish()
    }
}
