// Purpose: The C part of the Java<->C Wrapper, see also NcContext.java


#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "nexuschat-core-rust/nexuschat-ffi/nexuschat.h"


static nc_msg_t* get_nc_msg(JNIEnv *env, jobject obj);


// passing a NULL-jstring results in a NULL-ptr - this is needed by functions using eg. NULL for "delete"
#define CHAR_REF(a) \
    char* a##Ptr = char_ref__(env, (a));
static char* char_ref__(JNIEnv* env, jstring a) {
    if (a==NULL) {
        return NULL;
    }

    /* we do not use the JNI functions GetStringUTFChars()/ReleaseStringUTFChars()
    as they do not work on some older systems for code points >0xffff, eg. emojis.
    as a workaround, we're calling back to java-land's String.getBytes() which works as expected */
    static jclass    s_strCls    = NULL;
    static jmethodID s_getBytes  = NULL;
    static jclass    s_strEncode = NULL;
    if (s_getBytes==NULL) {
        s_strCls    = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/String"));
        s_getBytes  = (*env)->GetMethodID(env, s_strCls, "getBytes", "(Ljava/lang/String;)[B");
        s_strEncode = (*env)->NewGlobalRef(env, (*env)->NewStringUTF(env, "UTF-8"));
    }

    const jbyteArray stringJbytes = (jbyteArray)(*env)->CallObjectMethod(env, a, s_getBytes, s_strEncode);
    const jsize length = (*env)->GetArrayLength(env, stringJbytes);
    jbyte* pBytes = (*env)->GetByteArrayElements(env, stringJbytes, NULL);
    if (pBytes==NULL) {
        return NULL;
    }

    char* cstr = strndup((const char*)pBytes, length);

    (*env)->ReleaseByteArrayElements(env, stringJbytes, pBytes, JNI_ABORT);
    (*env)->DeleteLocalRef(env, stringJbytes);

    return cstr;
}

#define CHAR_UNREF(a) \
    free(a##Ptr);

#define JSTRING_NEW(a) jstring_new__(env, (a))
static jstring jstring_new__(JNIEnv* env, const char* a)
{
    if (a==NULL || a[0]==0) {
        return (*env)->NewStringUTF(env, "");
    }

    /* for non-empty strings, do not use NewStringUTF() as this is buggy on some Android versions.
    Instead, create the string using `new String(ByteArray, "UTF-8);` which seems to be programmed more properly.
    (eg. on KitKat a simple "SMILING FACE WITH SMILING EYES" (U+1F60A, UTF-8 F0 9F 98 8A) will let the app crash, reporting 0xF0 is a bad UTF-8 start,
    see http://stackoverflow.com/questions/12127817/android-ics-4-0-ndk-newstringutf-is-crashing-down-the-app ) */
    static jclass    s_strCls    = NULL;
    static jmethodID s_strCtor   = NULL;
    static jclass    s_strEncode = NULL;
    if (s_strCtor==NULL) {
        s_strCls    = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/String"));
        s_strCtor   = (*env)->GetMethodID(env, s_strCls, "<init>", "([BLjava/lang/String;)V");
        s_strEncode = (*env)->NewGlobalRef(env, (*env)->NewStringUTF(env, "UTF-8"));
    }

    int a_bytes = strlen(a);
    jbyteArray array = (*env)->NewByteArray(env, a_bytes);
        (*env)->SetByteArrayRegion(env, array, 0, a_bytes, (const jbyte*)a);
        jstring ret = (jstring) (*env)->NewObject(env, s_strCls, s_strCtor, array, s_strEncode);
    (*env)->DeleteLocalRef(env, array); /* we have to delete the reference as it is not returned to Java, AFAIK */

    return ret;
}


// convert c-timestamp to java-timestamp
#define JTIMESTAMP(a) (((jlong)a)*((jlong)1000))


// convert java-timestamp to c-timestamp
#define CTIMESTAMP(a) (((jlong)a)/((jlong)1000))


static jbyteArray ptr2jbyteArray(JNIEnv *env, const void* ptr, size_t len) {
    if (ptr == NULL || len <= 0) {
        return NULL;
    }
    jbyteArray ret = (*env)->NewByteArray(env, len);
    if (ret == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, ret, 0, len, (const jbyte*)ptr);
    return ret;
}


static jintArray nc_array2jintArray_n_unref(JNIEnv *env, nc_array_t* ca)
{
    /* takes a C-array of type nc_array_t and converts it it a Java-Array.
    then the C-array is freed and the Java-Array is returned. */
    int i, icnt = ca? nc_array_get_cnt(ca) : 0;
    jintArray ret = (*env)->NewIntArray(env, icnt); if (ret==NULL) { return NULL; }

    if (ca) {
        if (icnt) {
            jint* temp = calloc(icnt, sizeof(jint));
            for (i = 0; i < icnt; i++) {
                temp[i] = (jint)nc_array_get_id(ca, i);
            }
            (*env)->SetIntArrayRegion(env, ret, 0, icnt, temp);
            free(temp);
        }
        nc_array_unref(ca);
    }

    return ret;
}


static uint32_t* jintArray2uint32Pointer(JNIEnv* env, jintArray ja, int* ret_icnt)
{
    /* takes a Java-Array and converts it to a C-Array. */
    uint32_t* ret = NULL;
    if (ret_icnt) { *ret_icnt = 0; }

    if (env && ja && ret_icnt)
    {
        int i, icnt  = (*env)->GetArrayLength(env, ja);
        if (icnt > 0)
        {
            jint* temp = (*env)->GetIntArrayElements(env, ja, NULL);
            if (temp)
            {
                ret = calloc(icnt, sizeof(uint32_t));
                if (ret)
                {
                    for (i = 0; i < icnt; i++) {
                        ret[i] = (uint32_t)temp[i];
                    }
                    *ret_icnt = icnt;
                }
                (*env)->ReleaseIntArrayElements(env, ja, temp, 0);
            }
        }
    }

    return ret;
}


/************************************************************
 * NcEventChannel
 ************************************************************/

static nc_event_channel_t* get_nc_event_channel(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "eventChannelCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_event_channel_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT jlong Java_com_b44t_messenger_NcEventChannel_createEventChannelCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_event_channel_new();
}


JNIEXPORT void Java_com_b44t_messenger_NcEventChannel_unrefEventChannelCPtr(JNIEnv *env, jobject obj)
{
    nc_event_channel_unref(get_nc_event_channel(env, obj));
}


JNIEXPORT jlong Java_com_b44t_messenger_NcEventChannel_getEventEmitterCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_event_channel_get_event_emitter(get_nc_event_channel(env, obj));
}


/*******************************************************************************
 * NcAccounts
 ******************************************************************************/


static nc_accounts_t* get_nc_accounts(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "accountsCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_accounts_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT jlong Java_com_b44t_messenger_NcAccounts_createAccountsCPtr(JNIEnv *env, jobject obj, jstring dir, jobject chanObj)
{
    CHAR_REF(dir);
        int writable = 1;
        jlong accountsCPtr = (jlong)nc_accounts_new_with_event_channel(dirPtr, writable, get_nc_event_channel(env, chanObj));
    CHAR_UNREF(dir);
    return accountsCPtr;
}


JNIEXPORT void Java_com_b44t_messenger_NcAccounts_unrefAccountsCPtr(JNIEnv *env, jobject obj)
{
    nc_accounts_unref(get_nc_accounts(env, obj));
}


JNIEXPORT jlong Java_com_b44t_messenger_NcAccounts_getEventEmitterCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_accounts_get_event_emitter(get_nc_accounts(env, obj));
}

JNIEXPORT jlong Java_com_b44t_messenger_NcAccounts_getJsonrpcInstanceCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_jsonrpc_init(get_nc_accounts(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcAccounts_startIo(JNIEnv *env, jobject obj)
{
    nc_accounts_start_io(get_nc_accounts(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcAccounts_stopIo(JNIEnv *env, jobject obj)
{
    nc_accounts_stop_io(get_nc_accounts(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcAccounts_maybeNetwork(JNIEnv *env, jobject obj)
{
    nc_accounts_maybe_network(get_nc_accounts(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcAccounts_setPushDeviceToken(JNIEnv *env, jobject obj, jstring token)
{
    CHAR_REF(token);
        nc_accounts_set_push_device_token(get_nc_accounts(env, obj), tokenPtr);
    CHAR_UNREF(token);
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcAccounts_backgroundFetch(JNIEnv *env, jobject obj, jint timeout_seconds)
{
    return nc_accounts_background_fetch(get_nc_accounts(env, obj), timeout_seconds) != 0;
}


JNIEXPORT void Java_com_b44t_messenger_NcAccounts_stopBackgroundFetch(JNIEnv *env, jobject obj)
{
    nc_accounts_stop_background_fetch(get_nc_accounts(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcAccounts_migrateAccount(JNIEnv *env, jobject obj, jstring dbfile)
{
    CHAR_REF(dbfile);
        jint accountId = nc_accounts_migrate_account(get_nc_accounts(env, obj), dbfilePtr);
    CHAR_UNREF(dbfile);
    return accountId;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcAccounts_removeAccount(JNIEnv *env, jobject obj, jint accountId)
{
    return nc_accounts_remove_account(get_nc_accounts(env, obj), accountId) != 0;
}


JNIEXPORT jintArray Java_com_b44t_messenger_NcAccounts_getAll(JNIEnv *env, jobject obj)
{
    nc_array_t* ca = nc_accounts_get_all(get_nc_accounts(env, obj));
    return nc_array2jintArray_n_unref(env, ca);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcAccounts_getAccountCPtr(JNIEnv *env, jobject obj, jint accountId)
{
    return (jlong)nc_accounts_get_account(get_nc_accounts(env, obj), accountId);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcAccounts_getSelectedAccountCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_accounts_get_selected_account(get_nc_accounts(env, obj));
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcAccounts_selectAccount(JNIEnv *env, jobject obj, jint accountId)
{
    return nc_accounts_select_account(get_nc_accounts(env, obj), accountId) != 0;
}


/*******************************************************************************
 * NcContext
 ******************************************************************************/


static nc_context_t* get_nc_context(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "contextCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_context_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContext_createContextCPtr(JNIEnv *env, jobject obj, jstring osname, jstring dbfile)
{
    CHAR_REF(osname);
    CHAR_REF(dbfile)
        jlong contextCPtr = (jlong)nc_context_new(osnamePtr, dbfilePtr, NULL);
    CHAR_UNREF(dbfile)
    CHAR_UNREF(osname);
    return contextCPtr;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_open(JNIEnv *env, jobject obj, jstring passphrase)
{
    CHAR_REF(passphrase);
    jboolean ret = nc_context_open(get_nc_context(env, obj), passphrasePtr);
    CHAR_UNREF(passphrase);
    return ret;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_isOpen(JNIEnv *env, jobject obj)
{
    return nc_context_is_open(get_nc_context(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_unrefContextCPtr(JNIEnv *env, jobject obj)
{
    nc_context_unref(get_nc_context(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_getAccountId(JNIEnv *env, jobject obj)
{
    return (jint)nc_get_id(get_nc_context(env, obj));
}


/* NcContext - open/configure/connect/fetch */

JNIEXPORT void Java_com_b44t_messenger_NcContext_setStockTranslation(JNIEnv *env, jobject obj, jint stock_id, jstring translation)
{
    CHAR_REF(translation);
        nc_set_stock_translation(get_nc_context(env, obj), stock_id, translationPtr);
    CHAR_UNREF(translation)
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_setConfigFromQr(JNIEnv *env, jobject obj, jstring qr)
{
    CHAR_REF(qr);
        jboolean ret = nc_set_config_from_qr(get_nc_context(env, obj), qrPtr);
    CHAR_UNREF(qr);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getBlobdir(JNIEnv *env, jobject obj)
{
    char* temp = nc_get_blobdir(get_nc_context(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getLastError(JNIEnv *env, jobject obj)
{
    char* temp = nc_get_last_error(get_nc_context(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_configure(JNIEnv *env, jobject obj)
{
    nc_configure(get_nc_context(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_stopOngoingProcess(JNIEnv *env, jobject obj)
{
    nc_stop_ongoing_process(get_nc_context(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_isConfigured(JNIEnv *env, jobject obj)
{
    return (jint)nc_is_configured(get_nc_context(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_startIo(JNIEnv *env, jobject obj)
{
    nc_start_io(get_nc_context(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_stopIo(JNIEnv *env, jobject obj)
{
    nc_stop_io(get_nc_context(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_maybeNetwork(JNIEnv *env, jobject obj)
{
    nc_maybe_network(get_nc_context(env, obj));
}

JNIEXPORT jlong Java_com_b44t_messenger_NcContext_getEventEmitterCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_get_event_emitter(get_nc_context(env, obj));
}


/* NcContext - handle contacts */

JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_mayBeValidAddr(JNIEnv *env, jobject obj, jstring addr)
{
    CHAR_REF(addr);
        jboolean ret = nc_may_be_valid_addr(addrPtr);
    CHAR_UNREF(addr);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_lookupContactIdByAddr(JNIEnv *env, jobject obj, jstring addr)
{
    CHAR_REF(addr);
        jint ret = nc_lookup_contact_id_by_addr(get_nc_context(env, obj), addrPtr);
    CHAR_UNREF(addr);
    return ret;
}


JNIEXPORT jintArray Java_com_b44t_messenger_NcContext_getContacts(JNIEnv *env, jobject obj, jint flags, jstring query)
{
    CHAR_REF(query);
        nc_array_t* ca = nc_get_contacts(get_nc_context(env, obj), flags, queryPtr);
    CHAR_UNREF(query);
    return nc_array2jintArray_n_unref(env, ca);
}


JNIEXPORT jintArray Java_com_b44t_messenger_NcContext_getBlockedContacts(JNIEnv *env, jobject obj)
{
    nc_array_t* ca = nc_get_blocked_contacts(get_nc_context(env, obj));
    return nc_array2jintArray_n_unref(env, ca);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContext_getContactCPtr(JNIEnv *env, jobject obj, jint contact_id)
{
    return (jlong)nc_get_contact(get_nc_context(env, obj), contact_id);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_createContact(JNIEnv *env, jobject obj, jstring name, jstring addr)
{
    CHAR_REF(name);
    CHAR_REF(addr);
        jint ret = (jint)nc_create_contact(get_nc_context(env, obj), namePtr, addrPtr);
    CHAR_UNREF(addr);
    CHAR_UNREF(name);
    return ret;
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_blockContact(JNIEnv *env, jobject obj, jint contact_id, jint block)
{
    nc_block_contact(get_nc_context(env, obj), contact_id, block);
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_deleteContact(JNIEnv *env, jobject obj, jint contact_id)
{
    return (jboolean)nc_delete_contact(get_nc_context(env, obj), contact_id);
}


/* NcContext - handle chats */

JNIEXPORT jlong Java_com_b44t_messenger_NcContext_getChatlistCPtr(JNIEnv *env, jobject obj, jint listflags, jstring query, jint queryId)
{
    jlong ret;
    if (query) {
        CHAR_REF(query);
            ret = (jlong)nc_get_chatlist(get_nc_context(env, obj), listflags, queryPtr, queryId);
        CHAR_UNREF(query);
    }
    else {
        ret = (jlong)nc_get_chatlist(get_nc_context(env, obj), listflags, NULL, queryId);
    }
    return ret;
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContext_getChatCPtr(JNIEnv *env, jobject obj, jint chat_id)
{
    return (jlong)nc_get_chat(get_nc_context(env, obj), chat_id);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_getChatIdByContactId(JNIEnv *env, jobject obj, jint contact_id)
{
    return (jint)nc_get_chat_id_by_contact_id(get_nc_context(env, obj), contact_id);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_markseenMsgs(JNIEnv *env, jobject obj, jintArray msg_ids)
{
    int msg_ids_cnt = 0;
    uint32_t* msg_ids_ptr = jintArray2uint32Pointer(env, msg_ids, &msg_ids_cnt);
        nc_markseen_msgs(get_nc_context(env, obj), msg_ids_ptr, msg_ids_cnt);
    free(msg_ids_ptr);
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getChatEncrInfo(JNIEnv *env, jobject obj, jint chat_id)
{
    char* temp = nc_get_chat_encrinfo(get_nc_context(env, obj), chat_id);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_marknoticedChat(JNIEnv *env, jobject obj, jint chat_id)
{
    nc_marknoticed_chat(get_nc_context(env, obj), chat_id);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_setChatVisibility(JNIEnv *env, jobject obj, jint chat_id, jint visibility)
{
    nc_set_chat_visibility(get_nc_context(env, obj), chat_id, visibility);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_createChatByContactId(JNIEnv *env, jobject obj, jint contact_id)
{
    return (jint)nc_create_chat_by_contact_id(get_nc_context(env, obj), contact_id);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_createGroupChat(JNIEnv *env, jobject obj, jstring name)
{
    CHAR_REF(name);
        jint ret = (jint)nc_create_group_chat(get_nc_context(env, obj), 0, namePtr);
    CHAR_UNREF(name);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_createBroancastList(JNIEnv *env, jobject obj)
{
    return (jint)nc_create_broancast_list(get_nc_context(env, obj));
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_isContactInChat(JNIEnv *env, jobject obj, jint chat_id, jint contact_id)
{
    return (jboolean)nc_is_contact_in_chat(get_nc_context(env, obj), chat_id, contact_id);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_addContactToChat(JNIEnv *env, jobject obj, jint chat_id, jint contact_id)
{
    return (jint)nc_add_contact_to_chat(get_nc_context(env, obj), chat_id, contact_id);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_removeContactFromChat(JNIEnv *env, jobject obj, jint chat_id, jint contact_id)
{
    return (jint)nc_remove_contact_from_chat(get_nc_context(env, obj), chat_id, contact_id);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_setDraft(JNIEnv *env, jobject obj, jint chat_id, jobject msg /* NULL=delete */)
{
    nc_set_draft(get_nc_context(env, obj), chat_id, get_nc_msg(env, msg));
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContext_getDraftCPtr(JNIEnv *env, jobject obj, jint chat_id)
{
    return (jlong)nc_get_draft(get_nc_context(env, obj), chat_id);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_setChatName(JNIEnv *env, jobject obj, jint chat_id, jstring name)
{
    CHAR_REF(name);
        jint ret = (jint)nc_set_chat_name(get_nc_context(env, obj), chat_id, namePtr);
    CHAR_UNREF(name);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_setChatProfileImage(JNIEnv *env, jobject obj, jint chat_id, jstring image/*NULL=delete*/)
{
    CHAR_REF(image);
        jint ret = (jint)nc_set_chat_profile_image(get_nc_context(env, obj), chat_id, imagePtr/*CHAR_REF() preserves NULL*/);
    CHAR_UNREF(image);
    return ret;
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_deleteChat(JNIEnv *env, jobject obj, jint chat_id)
{
    nc_delete_chat(get_nc_context(env, obj), chat_id);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_blockChat(JNIEnv *env, jobject obj, jint chat_id)
{
    nc_block_chat(get_nc_context(env, obj), chat_id);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_acceptChat(JNIEnv *env, jobject obj, jint chat_id)
{
    nc_accept_chat(get_nc_context(env, obj), chat_id);
}


/* NcContext - handle messages */


JNIEXPORT jint Java_com_b44t_messenger_NcContext_getFreshMsgCount(JNIEnv *env, jobject obj, jint chat_id)
{
    return nc_get_fresh_msg_cnt(get_nc_context(env, obj), chat_id);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_estimateDeletionCount(JNIEnv *env, jobject obj, jboolean from_server, jlong seconds)
{
    return nc_estimate_deletion_cnt(get_nc_context(env, obj), from_server, seconds);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContext_getMsgCPtr(JNIEnv *env, jobject obj, jint id)
{
    return (jlong)nc_get_msg(get_nc_context(env, obj), id);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContext_createMsgCPtr(JNIEnv *env, jobject obj, jint viewtype)
{
    return (jlong)nc_msg_new(get_nc_context(env, obj), viewtype);
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getMsgInfo(JNIEnv *env, jobject obj, jint msg_id)
{
    char* temp = nc_get_msg_info(get_nc_context(env, obj), msg_id);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_sendEditRequest(JNIEnv *env, jobject obj, jint msg_id, jstring text)
{
    CHAR_REF(text);
        nc_send_edit_request(get_nc_context(env, obj), msg_id, textPtr);
    CHAR_UNREF(text);
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getMsgHtml(JNIEnv *env, jobject obj, jint msg_id)
{
    char* temp = nc_get_msg_html(get_nc_context(env, obj), msg_id);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_downloadFullMsg(JNIEnv *env, jobject obj, jint msg_id)
{
    nc_download_full_msg(get_nc_context(env, obj), msg_id);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_deleteMsgs(JNIEnv *env, jobject obj, jintArray msg_ids)
{
    int msg_ids_cnt = 0;
    uint32_t* msg_ids_ptr = jintArray2uint32Pointer(env, msg_ids, &msg_ids_cnt);
        nc_delete_msgs(get_nc_context(env, obj), msg_ids_ptr, msg_ids_cnt);
    free(msg_ids_ptr);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_sendDeleteRequest(JNIEnv *env, jobject obj, jintArray msg_ids)
{
    int msg_ids_cnt = 0;
    uint32_t* msg_ids_ptr = jintArray2uint32Pointer(env, msg_ids, &msg_ids_cnt);
        nc_send_delete_request(get_nc_context(env, obj), msg_ids_ptr, msg_ids_cnt);
    free(msg_ids_ptr);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_forwardMsgs(JNIEnv *env, jobject obj, jintArray msg_ids, jint chat_id)
{
    int msg_ids_cnt = 0;
    uint32_t* msg_ids_ptr = jintArray2uint32Pointer(env, msg_ids, &msg_ids_cnt);
        nc_forward_msgs(get_nc_context(env, obj), msg_ids_ptr, msg_ids_cnt, chat_id);
    free(msg_ids_ptr);
}

JNIEXPORT void Java_com_b44t_messenger_NcContext_saveMsgs(JNIEnv *env, jobject obj, jintArray msg_ids)
{
    int msg_ids_cnt = 0;
    uint32_t* msg_ids_ptr = jintArray2uint32Pointer(env, msg_ids, &msg_ids_cnt);
        nc_save_msgs(get_nc_context(env, obj), msg_ids_ptr, msg_ids_cnt);
    free(msg_ids_ptr);
}

JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_resendMsgs(JNIEnv *env, jobject obj, jintArray msg_ids)
{
    int msg_ids_cnt = 0;
    uint32_t* msg_ids_ptr = jintArray2uint32Pointer(env, msg_ids, &msg_ids_cnt);
        jboolean ret = nc_resend_msgs(get_nc_context(env, obj), msg_ids_ptr, msg_ids_cnt) != 0;
    free(msg_ids_ptr);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_sendMsg(JNIEnv *env, jobject obj, jint chat_id, jobject msg)
{
    return nc_send_msg(get_nc_context(env, obj), chat_id, get_nc_msg(env, msg));
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_sendTextMsg(JNIEnv *env, jobject obj, jint chat_id, jstring text)
{
    CHAR_REF(text);
        jint msg_id = nc_send_text_msg(get_nc_context(env, obj), chat_id, textPtr);
    CHAR_UNREF(text);
    return msg_id;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_sendWebxncStatusUpdate(JNIEnv *env, jobject obj, jint msg_id, jstring payload)
{
    CHAR_REF(payload);
        jboolean ret = nc_send_webxnc_status_update(get_nc_context(env, obj), msg_id, payloadPtr, NULL) != 0;
    CHAR_UNREF(payload);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getWebxncStatusUpdates(JNIEnv *env, jobject obj, jint msg_id, jint last_known_serial)
{
    char* temp = nc_get_webxnc_status_updates(get_nc_context(env, obj), msg_id, last_known_serial);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_initWebxncIntegration(JNIEnv *env, jobject obj, jint chat_id)
{
    return nc_init_webxnc_integration(get_nc_context(env, obj), chat_id);
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_setWebxncIntegration(JNIEnv *env, jobject obj, jstring file)
{
    CHAR_REF(file);
        nc_set_webxnc_integration(get_nc_context(env, obj), filePtr);
    CHAR_UNREF(file);
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_addDeviceMsg(JNIEnv *env, jobject obj, jstring label, jobject msg)
{
    CHAR_REF(label);
        int msg_id = nc_add_device_msg(get_nc_context(env, obj), labelPtr, get_nc_msg(env, msg));
    CHAR_UNREF(label);
    return msg_id;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_wasDeviceMsgEverAdded(JNIEnv *env, jobject obj, jstring label)
{
    CHAR_REF(label);
        jboolean ret = nc_was_device_msg_ever_added(get_nc_context(env, obj), labelPtr) != 0;
    CHAR_UNREF(label);
    return ret;
}


/* NcContext - handle config */

JNIEXPORT void Java_com_b44t_messenger_NcContext_setConfig(JNIEnv *env, jobject obj, jstring key, jstring value /*may be NULL*/)
{
    CHAR_REF(key);
    CHAR_REF(value);
        nc_set_config(get_nc_context(env, obj), keyPtr, valuePtr /*is NULL if value is NULL, CHAR_REF() handles this*/);
    CHAR_UNREF(key);
    CHAR_UNREF(value);
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getConfig(JNIEnv *env, jobject obj, jstring key)
{
    CHAR_REF(key);
        char* temp = nc_get_config(get_nc_context(env, obj), keyPtr);
            jstring ret = NULL;
            if (temp) {
                ret = JSTRING_NEW(temp);
            }
        nc_str_unref(temp);
    CHAR_UNREF(key);
    return ret;
}


/* NcContext - out-of-band verification */

JNIEXPORT jlong Java_com_b44t_messenger_NcContext_checkQrCPtr(JNIEnv *env, jobject obj, jstring qr)
{
    CHAR_REF(qr);
        jlong ret = (jlong)nc_check_qr(get_nc_context(env, obj), qrPtr);
    CHAR_UNREF(qr);
    return ret;
}

JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getSecurejoinQr(JNIEnv *env, jobject obj, jint chat_id)
{
    char* temp = nc_get_securejoin_qr(get_nc_context(env, obj), chat_id);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}

JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getSecurejoinQrSvg(JNIEnv *env, jobject obj, jint chat_id)
{
    char* temp = nc_get_securejoin_qr_svg(get_nc_context(env, obj), chat_id);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}

JNIEXPORT jstring Java_com_b44t_messenger_NcContext_createQrSvg(JNIEnv *env, jobject obj, jstring payload)
{
    CHAR_REF(payload);
        char* temp = nc_create_qr_svg(payloadPtr);
             jstring ret = JSTRING_NEW(temp);
        nc_str_unref(temp);
    CHAR_UNREF(payload);
    return ret;
}

JNIEXPORT jint Java_com_b44t_messenger_NcContext_joinSecurejoin(JNIEnv *env, jobject obj, jstring qr)
{
    CHAR_REF(qr);
        jint ret = nc_join_securejoin(get_nc_context(env, obj), qrPtr);
    CHAR_UNREF(qr);
    return ret;
}


/* NcContext - misc. */

JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getInfo(JNIEnv *env, jobject obj)
{
    char* temp = nc_get_info(get_nc_context(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_getConnectivity(JNIEnv *env, jobject obj)
{
    return nc_get_connectivity(get_nc_context(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getConnectivityHtml(JNIEnv *env, jobject obj)
{
    char* temp = nc_get_connectivity_html(get_nc_context(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getOauth2Url(JNIEnv *env, jobject obj, jstring addr, jstring redirectUrl)
{
    CHAR_REF(addr);
    CHAR_REF(redirectUrl);
    char* temp = nc_get_oauth2_url(get_nc_context(env, obj), addrPtr, redirectUrlPtr);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    CHAR_UNREF(redirectUrl);
    CHAR_UNREF(addr);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_getContactEncrInfo(JNIEnv *env, jobject obj, jint contact_id)
{
    char* temp = nc_get_contact_encrinfo(get_nc_context(env, obj), contact_id);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_imex(JNIEnv *env, jobject obj, jint what, jstring dir)
{
    CHAR_REF(dir);
        nc_imex(get_nc_context(env, obj), what, dirPtr, "");
    CHAR_UNREF(dir);
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContext_imexHasBackup(JNIEnv *env, jobject obj, jstring dir)
{
    CHAR_REF(dir);
        jstring ret = NULL;
        char* temp = nc_imex_has_backup(get_nc_context(env, obj),  dirPtr);
        if (temp) {
            ret = JSTRING_NEW(temp);
            nc_str_unref(temp);
        }
    CHAR_UNREF(dir);
    return ret; /* may be NULL! */
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContext_newBackupProviderCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_backup_provider_new(get_nc_context(env, obj));
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_receiveBackup(JNIEnv *env, jobject obj, jstring qr)
{
    CHAR_REF(qr);
        jboolean ret = nc_receive_backup(get_nc_context(env, obj), qrPtr);
    CHAR_UNREF(qr);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContext_addAddressBook(JNIEnv *env, jobject obj, jstring adrbook)
{
    CHAR_REF(adrbook);
        int modify_count = nc_add_address_book(get_nc_context(env, obj), adrbookPtr);
    CHAR_UNREF(adrbook);
    return modify_count;
}


JNIEXPORT void Java_com_b44t_messenger_NcContext_sendLocationsToChat(JNIEnv *env, jobject obj, jint chat_id, jint seconds)
{
    nc_send_locations_to_chat(get_nc_context(env, obj), chat_id, seconds);
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_isSendingLocationsToChat(JNIEnv *env, jobject obj, jint chat_id)
{
    return (nc_is_sending_locations_to_chat(get_nc_context(env, obj), chat_id)!=0);
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_setLocation(JNIEnv *env, jobject obj, jfloat latitude, jfloat longitude, jfloat accuracy)
{
    return (nc_set_location(get_nc_context(env, obj), latitude, longitude, accuracy)!=0);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContext_getProviderFromEmailWithDnsCPtr(JNIEnv *env, jobject obj, jstring email)
{
    CHAR_REF(email);
        jlong ret = (jlong)nc_provider_new_from_email_with_dns(get_nc_context(env, obj), emailPtr);
    CHAR_UNREF(email);
    return ret;
}


/*******************************************************************************
 * NcEventEmitter
 ******************************************************************************/


static nc_event_emitter_t* get_nc_event_emitter(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "eventEmitterCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_event_emitter_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcEventEmitter_unrefEventEmitterCPtr(JNIEnv *env, jobject obj)
{
    nc_event_emitter_unref(get_nc_event_emitter(env, obj));
}


JNIEXPORT jlong Java_com_b44t_messenger_NcEventEmitter_getNextEventCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_get_next_event(get_nc_event_emitter(env, obj));
}


/*******************************************************************************
 * NcEvent
 ******************************************************************************/


static nc_event_t* get_nc_event(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "eventCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_event_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcEvent_unrefEventCPtr(JNIEnv *env, jobject obj)
{
    nc_event_unref(get_nc_event(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcEvent_getId(JNIEnv *env, jobject obj)
{
    return nc_event_get_id(get_nc_event(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcEvent_getData1Int(JNIEnv *env, jobject obj)
{
    return nc_event_get_data1_int(get_nc_event(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcEvent_getData2Int(JNIEnv *env, jobject obj)
{
    return nc_event_get_data2_int(get_nc_event(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcEvent_getData2Str(JNIEnv *env, jobject obj)
{
    char* temp = nc_event_get_data2_str(get_nc_event(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jbyteArray Java_com_b44t_messenger_NcEvent_getData2Blob(JNIEnv *env, jobject obj)
{
    jbyteArray ret = NULL;
    nc_event_t* event = get_nc_event(env, obj);

    size_t ptrSize = nc_event_get_data2_int(event);
    char* ptr = nc_event_get_data2_str(get_nc_event(env, obj));
        ret = ptr2jbyteArray(env, ptr, ptrSize);
    nc_str_unref(ptr);

    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcEvent_getAccountId(JNIEnv *env, jobject obj)
{
    return (jint)nc_event_get_account_id(get_nc_event(env, obj));
}


/*******************************************************************************
 * NcChatlist
 ******************************************************************************/


static nc_chatlist_t* get_nc_chatlist(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "chatlistCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_chatlist_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcChatlist_unrefChatlistCPtr(JNIEnv *env, jobject obj)
{
    nc_chatlist_unref(get_nc_chatlist(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcChatlist_getCnt(JNIEnv *env, jobject obj)
{
    return nc_chatlist_get_cnt(get_nc_chatlist(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcChatlist_getChatId(JNIEnv *env, jobject obj, jint index)
{
    return nc_chatlist_get_chat_id(get_nc_chatlist(env, obj), index);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcChatlist_getChatCPtr(JNIEnv *env, jobject obj, jint index)
{
    nc_chatlist_t* chatlist = get_nc_chatlist(env, obj);
    return (jlong)nc_get_chat(nc_chatlist_get_context(chatlist), nc_chatlist_get_chat_id(chatlist, index));
}


JNIEXPORT jint Java_com_b44t_messenger_NcChatlist_getMsgId(JNIEnv *env, jobject obj, jint index)
{
    return nc_chatlist_get_msg_id(get_nc_chatlist(env, obj), index);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcChatlist_getMsgCPtr(JNIEnv *env, jobject obj, jint index)
{
    nc_chatlist_t* chatlist = get_nc_chatlist(env, obj);
    return (jlong)nc_get_msg(nc_chatlist_get_context(chatlist), nc_chatlist_get_msg_id(chatlist, index));
}


JNIEXPORT jlong Java_com_b44t_messenger_NcChatlist_getSummaryCPtr(JNIEnv *env, jobject obj, jint index, jlong chatCPtr)
{
    return (jlong)nc_chatlist_get_summary(get_nc_chatlist(env, obj), index, (nc_chat_t*)chatCPtr);
}


/*******************************************************************************
 * NcChat
 ******************************************************************************/


static nc_chat_t* get_nc_chat(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "chatCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_chat_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcChat_unrefChatCPtr(JNIEnv *env, jobject obj)
{
    nc_chat_unref(get_nc_chat(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcChat_getId(JNIEnv *env, jobject obj)
{
    return nc_chat_get_id(get_nc_chat(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcChat_getType(JNIEnv *env, jobject obj)
{
    return nc_chat_get_type(get_nc_chat(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcChat_getVisibility(JNIEnv *env, jobject obj)
{
    return nc_chat_get_visibility(get_nc_chat(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcChat_getName(JNIEnv *env, jobject obj)
{
    char* temp = nc_chat_get_name(get_nc_chat(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcChat_getMailinglistAddr(JNIEnv *env, jobject obj)
{
    char* temp = nc_chat_get_mailinglist_addr(get_nc_chat(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcChat_getProfileImage(JNIEnv *env, jobject obj)
{
    char* temp = nc_chat_get_profile_image(get_nc_chat(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcChat_getColor(JNIEnv *env, jobject obj)
{
    return nc_chat_get_color(get_nc_chat(env, obj));
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcChat_isEncrypted(JNIEnv *env, jobject obj)
{
    return nc_chat_is_encrypted(get_nc_chat(env, obj))!=0;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcChat_isUnpromoted(JNIEnv *env, jobject obj)
{
    return nc_chat_is_unpromoted(get_nc_chat(env, obj))!=0;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcChat_isSelfTalk(JNIEnv *env, jobject obj)
{
    return nc_chat_is_self_talk(get_nc_chat(env, obj))!=0;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcChat_isDeviceTalk(JNIEnv *env, jobject obj)
{
    return nc_chat_is_device_talk(get_nc_chat(env, obj))!=0;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcChat_canSend(JNIEnv *env, jobject obj)
{
    return nc_chat_can_send(get_nc_chat(env, obj))!=0;
}



JNIEXPORT jboolean Java_com_b44t_messenger_NcChat_isSendingLocations(JNIEnv *env, jobject obj)
{
    return nc_chat_is_sending_locations(get_nc_chat(env, obj))!=0;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcChat_isContactRequest(JNIEnv *env, jobject obj)
{
    return nc_chat_is_contact_request(get_nc_chat(env, obj))!=0;
}


JNIEXPORT jintArray Java_com_b44t_messenger_NcContext_getChatMedia(JNIEnv *env, jobject obj, jint chat_id, jint type1, jint type2, jint type3)
{
    nc_array_t* ca = nc_get_chat_media(get_nc_context(env, obj), chat_id, type1, type2, type3);
    return nc_array2jintArray_n_unref(env, ca);
}


JNIEXPORT jintArray Java_com_b44t_messenger_NcContext_getChatMsgs(JNIEnv *env, jobject obj, jint chat_id, jint flags, jint marker1before)
{
    nc_array_t* ca = nc_get_chat_msgs(get_nc_context(env, obj), chat_id, flags, marker1before);
    return nc_array2jintArray_n_unref(env, ca);
}


JNIEXPORT jintArray Java_com_b44t_messenger_NcContext_searchMsgs(JNIEnv *env, jobject obj, jint chat_id, jstring query)
{
    CHAR_REF(query);
        nc_array_t* ca = nc_search_msgs(get_nc_context(env, obj), chat_id, queryPtr);
    CHAR_UNREF(query);
    return nc_array2jintArray_n_unref(env, ca);
}


JNIEXPORT jintArray Java_com_b44t_messenger_NcContext_getFreshMsgs(JNIEnv *env, jobject obj)
{
    nc_array_t* ca = nc_get_fresh_msgs(get_nc_context(env, obj));
    return nc_array2jintArray_n_unref(env, ca);
}


JNIEXPORT jintArray Java_com_b44t_messenger_NcContext_getChatContacts(JNIEnv *env, jobject obj, jint chat_id)
{
    nc_array_t* ca = nc_get_chat_contacts(get_nc_context(env, obj), chat_id);
    return nc_array2jintArray_n_unref(env, ca);
}

JNIEXPORT jint Java_com_b44t_messenger_NcContext_getChatEphemeralTimer(JNIEnv *env, jobject obj, jint chat_id)
{
    return nc_get_chat_ephemeral_timer(get_nc_context(env, obj), chat_id);
}

JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_setChatEphemeralTimer(JNIEnv *env, jobject obj, jint chat_id, jint timer)
{
    return nc_set_chat_ephemeral_timer(get_nc_context(env, obj), chat_id, timer);
}

JNIEXPORT jboolean Java_com_b44t_messenger_NcContext_setChatMuteDuration(JNIEnv *env, jobject obj, jint chat_id, jlong duration)
{
    return nc_set_chat_mute_duration(get_nc_context(env, obj), chat_id, duration);
}

JNIEXPORT jboolean Java_com_b44t_messenger_NcChat_isMuted(JNIEnv *env, jobject obj)
{
    return nc_chat_is_muted(get_nc_chat(env, obj));
}


/*******************************************************************************
 * NcMsg
 ******************************************************************************/


static nc_msg_t* get_nc_msg(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (env && obj) {
        if (fid==0) {
            jclass cls = (*env)->GetObjectClass(env, obj);
            fid = (*env)->GetFieldID(env, cls, "msgCPtr", "J" /*Signature, J=long*/);
        }
        if (fid) {
            return (nc_msg_t*)(*env)->GetLongField(env, obj, fid);
        }
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcMsg_unrefMsgCPtr(JNIEnv *env, jobject obj)
{
    nc_msg_unref(get_nc_msg(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getId(JNIEnv *env, jobject obj)
{
    return nc_msg_get_id(get_nc_msg(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getText(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_text(get_nc_msg(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getSubject(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_subject(get_nc_msg(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jlong Java_com_b44t_messenger_NcMsg_getTimestamp(JNIEnv *env, jobject obj)
{
    return JTIMESTAMP(nc_msg_get_timestamp(get_nc_msg(env, obj)));
}


JNIEXPORT jlong Java_com_b44t_messenger_NcMsg_getSortTimestamp(JNIEnv *env, jobject obj)
{
    return JTIMESTAMP(nc_msg_get_sort_timestamp(get_nc_msg(env, obj)));
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcMsg_hasDeviatingTimestamp(JNIEnv *env, jobject obj)
{
    return nc_msg_has_deviating_timestamp(get_nc_msg(env, obj))!=0;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcMsg_hasLocation(JNIEnv *env, jobject obj)
{
    return nc_msg_has_location(get_nc_msg(env, obj))!=0;
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getType(JNIEnv *env, jobject obj)
{
    return nc_msg_get_viewtype(get_nc_msg(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getInfoType(JNIEnv *env, jobject obj)
{
    return nc_msg_get_info_type(get_nc_msg(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getInfoContactId(JNIEnv *env, jobject obj)
{
    return nc_msg_get_info_contact_id(get_nc_msg(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getState(JNIEnv *env, jobject obj)
{
    return nc_msg_get_state(get_nc_msg(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getDownloadState(JNIEnv *env, jobject obj)
{
    return nc_msg_get_download_state(get_nc_msg(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getChatId(JNIEnv *env, jobject obj)
{
    return nc_msg_get_chat_id(get_nc_msg(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getFromId(JNIEnv *env, jobject obj)
{
    return nc_msg_get_from_id(get_nc_msg(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getWidth(JNIEnv *env, jobject obj, jint def)
{
    jint ret = (jint)nc_msg_get_width(get_nc_msg(env, obj));
    return ret? ret : def;
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getHeight(JNIEnv *env, jobject obj, jint def)
{
    jint ret = (jint)nc_msg_get_height(get_nc_msg(env, obj));
    return ret? ret : def;
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getDuration(JNIEnv *env, jobject obj)
{
    return nc_msg_get_duration(get_nc_msg(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcMsg_lateFilingMediaSize(JNIEnv *env, jobject obj, jint width, jint height, jint duration)
{
    nc_msg_latefiling_mediasize(get_nc_msg(env, obj), width, height, duration);
}


JNIEXPORT jlong Java_com_b44t_messenger_NcMsg_getFilebytes(JNIEnv *env, jobject obj)
{
    return (jlong)nc_msg_get_filebytes(get_nc_msg(env, obj));
}


JNIEXPORT jlong Java_com_b44t_messenger_NcMsg_getSummaryCPtr(JNIEnv *env, jobject obj, jlong chatCPtr)
{
    return (jlong)nc_msg_get_summary(get_nc_msg(env, obj), (nc_chat_t*)chatCPtr);
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getSummarytext(JNIEnv *env, jobject obj, jint approx_characters)
{
    char* temp = nc_msg_get_summarytext(get_nc_msg(env, obj), approx_characters);
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getOverrideSenderName(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_override_sender_name(get_nc_msg(env, obj));
        jstring ret = NULL;
        if (temp) {
            ret = JSTRING_NEW(temp);
        }
    nc_str_unref(temp);
    return ret; // null if there is no override-sender-name
}


JNIEXPORT jint Java_com_b44t_messenger_NcMsg_showPadlock(JNIEnv *env, jobject obj)
{
    return nc_msg_get_showpadlock(get_nc_msg(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getFile(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_file(get_nc_msg(env, obj));
        jstring ret =  JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getFilemime(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_filemime(get_nc_msg(env, obj));
        jstring ret =  JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getFilename(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_filename(get_nc_msg(env, obj));
        jstring ret =  JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jbyteArray Java_com_b44t_messenger_NcMsg_getWebxncBlob(JNIEnv *env, jobject obj, jstring filename)
{
    jbyteArray ret = NULL;
    CHAR_REF(filename)
        size_t ptrSize = 0;
        char* ptr = nc_msg_get_webxnc_blob(get_nc_msg(env, obj), filenamePtr, &ptrSize);
        ret = ptr2jbyteArray(env, ptr, ptrSize);
        nc_str_unref(ptr);
    CHAR_UNREF(filename)
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getWebxncInfoJson(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_webxnc_info(get_nc_msg(env, obj));
        jstring ret =  JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getWebxncHref(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_webxnc_href(get_nc_msg(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcMsg_isForwarded(JNIEnv *env, jobject obj)
{
    return nc_msg_is_forwarded(get_nc_msg(env, obj))!=0;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcMsg_isInfo(JNIEnv *env, jobject obj)
{
    return nc_msg_is_info(get_nc_msg(env, obj))!=0;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcMsg_hasHtml(JNIEnv *env, jobject obj)
{
    return nc_msg_has_html(get_nc_msg(env, obj))!=0;
}


JNIEXPORT void Java_com_b44t_messenger_NcMsg_setText(JNIEnv *env, jobject obj, jstring text)
{
    CHAR_REF(text);
        nc_msg_set_text(get_nc_msg(env, obj), textPtr);
    CHAR_UNREF(text);
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcMsg_isEdited(JNIEnv *env, jobject obj)
{
    return nc_msg_is_edited(get_nc_msg(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcMsg_setFileAndDeduplicate(JNIEnv *env, jobject obj, jstring file, jstring name, jstring filemime)
{
    CHAR_REF(file);
    CHAR_REF(name);
    CHAR_REF(filemime);
    nc_msg_set_file_and_deduplicate(get_nc_msg(env, obj), filePtr, namePtr, filemimePtr);
    CHAR_UNREF(filemime);
    CHAR_UNREF(name);
    CHAR_UNREF(file);
}


JNIEXPORT void Java_com_b44t_messenger_NcMsg_setDimension(JNIEnv *env, jobject obj, int width, int height)
{
    nc_msg_set_dimension(get_nc_msg(env, obj), width, height);
}


JNIEXPORT void Java_com_b44t_messenger_NcMsg_setDuration(JNIEnv *env, jobject obj, int duration)
{
    nc_msg_set_duration(get_nc_msg(env, obj), duration);
}


JNIEXPORT void Java_com_b44t_messenger_NcMsg_setLocation(JNIEnv *env, jobject obj, jfloat latitude, jfloat longitude)
{
    nc_msg_set_location(get_nc_msg(env, obj), latitude, longitude);
}


JNIEXPORT void Java_com_b44t_messenger_NcMsg_setQuoteCPtr(JNIEnv *env, jobject obj, jlong quoteCPtr)
{
    nc_msg_set_quote(get_nc_msg(env, obj), (nc_msg_t*)quoteCPtr);
}


JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getQuotedText(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_quoted_text(get_nc_msg(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jlong Java_com_b44t_messenger_NcMsg_getQuotedMsgCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_msg_get_quoted_msg(get_nc_msg(env, obj));
}


JNIEXPORT jlong Java_com_b44t_messenger_NcMsg_getParentCPtr(JNIEnv *env, jobject obj)
{
    return (jlong)nc_msg_get_parent(get_nc_msg(env, obj));
}

JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getOriginalMsgId(JNIEnv *env, jobject obj)
{
    return (jint)nc_msg_get_original_msg_id(get_nc_msg(env, obj));
}

JNIEXPORT jint Java_com_b44t_messenger_NcMsg_getSavedMsgId(JNIEnv *env, jobject obj)
{
    return (jint)nc_msg_get_saved_msg_id(get_nc_msg(env, obj));
}

JNIEXPORT jstring Java_com_b44t_messenger_NcMsg_getError(JNIEnv *env, jobject obj)
{
    char* temp = nc_msg_get_error(get_nc_msg(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


/*******************************************************************************
 * NcContact
 ******************************************************************************/


static nc_contact_t* get_nc_contact(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "contactCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_contact_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcContact_unrefContactCPtr(JNIEnv *env, jobject obj)
{
    nc_contact_unref(get_nc_contact(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcContact_getId(JNIEnv *env, jobject obj)
{
    return nc_contact_get_id(get_nc_contact(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContact_getName(JNIEnv *env, jobject obj)
{
    char* temp = nc_contact_get_name(get_nc_contact(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContact_getAuthName(JNIEnv *env, jobject obj)
{
    char* temp = nc_contact_get_auth_name(get_nc_contact(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContact_getDisplayName(JNIEnv *env, jobject obj)
{
    char* temp = nc_contact_get_display_name(get_nc_contact(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContact_getAddr(JNIEnv *env, jobject obj)
{
    char* temp = nc_contact_get_addr(get_nc_contact(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContact_getProfileImage(JNIEnv *env, jobject obj)
{
    char* temp = nc_contact_get_profile_image(get_nc_contact(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContact_getColor(JNIEnv *env, jobject obj)
{
    return nc_contact_get_color(get_nc_contact(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcContact_getStatus(JNIEnv *env, jobject obj)
{
    char* temp = nc_contact_get_status(get_nc_contact(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jlong Java_com_b44t_messenger_NcContact_getLastSeen(JNIEnv *env, jobject obj)
{
    return JTIMESTAMP(nc_contact_get_last_seen(get_nc_contact(env, obj)));
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContact_wasSeenRecently(JNIEnv *env, jobject obj)
{
    return (jboolean)(nc_contact_was_seen_recently(get_nc_contact(env, obj))!=0);
}

JNIEXPORT jboolean Java_com_b44t_messenger_NcContact_isBlocked(JNIEnv *env, jobject obj)
{
    return (jboolean)(nc_contact_is_blocked(get_nc_contact(env, obj))!=0);
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContact_isVerified(JNIEnv *env, jobject obj)
{
    return nc_contact_is_verified(get_nc_contact(env, obj))==2;
}


JNIEXPORT jboolean Java_com_b44t_messenger_NcContact_isKeyContact(JNIEnv *env, jobject obj)
{
    return nc_contact_is_key_contact(get_nc_contact(env, obj))==1;
}


JNIEXPORT jint Java_com_b44t_messenger_NcContact_getVerifierId(JNIEnv *env, jobject obj)
{
    return nc_contact_get_verifier_id(get_nc_contact(env, obj));
}

JNIEXPORT jboolean Java_com_b44t_messenger_NcContact_isBot(JNIEnv *env, jobject obj)
{
    return nc_contact_is_bot(get_nc_contact(env, obj)) != 0;
}


/*******************************************************************************
 * NcLot
 ******************************************************************************/


static nc_lot_t* get_nc_lot(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "lotCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_lot_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcLot_getText1(JNIEnv *env, jobject obj)
{
    char* temp = nc_lot_get_text1(get_nc_lot(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jint Java_com_b44t_messenger_NcLot_getText1Meaning(JNIEnv *env, jobject obj)
{
    return nc_lot_get_text1_meaning(get_nc_lot(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcLot_getText2(JNIEnv *env, jobject obj)
{
    char* temp = nc_lot_get_text2(get_nc_lot(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jlong Java_com_b44t_messenger_NcLot_getTimestamp(JNIEnv *env, jobject obj)
{
    return JTIMESTAMP(nc_lot_get_timestamp(get_nc_lot(env, obj)));
}


JNIEXPORT jint Java_com_b44t_messenger_NcLot_getState(JNIEnv *env, jobject obj)
{
    return nc_lot_get_state(get_nc_lot(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcLot_getId(JNIEnv *env, jobject obj)
{
    return nc_lot_get_id(get_nc_lot(env, obj));
}


JNIEXPORT void Java_com_b44t_messenger_NcLot_unrefLotCPtr(JNIEnv *env, jobject obj)
{
    nc_lot_unref(get_nc_lot(env, obj));
}


/*******************************************************************************
 * NcBackupProvider
 ******************************************************************************/


static nc_backup_provider_t* get_nc_backup_provider(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "backupProviderCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_backup_provider_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcBackupProvider_unrefBackupProviderCPtr(JNIEnv *env, jobject obj)
{
    nc_backup_provider_unref(get_nc_backup_provider(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcBackupProvider_getQr(JNIEnv *env, jobject obj)
{
    char* temp = nc_backup_provider_get_qr(get_nc_backup_provider(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcBackupProvider_getQrSvg(JNIEnv *env, jobject obj)
{
    char* temp = nc_create_qr_svg(nc_backup_provider_get_qr(get_nc_backup_provider(env, obj)));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT void Java_com_b44t_messenger_NcBackupProvider_waitForReceiver(JNIEnv *env, jobject obj)
{
    nc_backup_provider_wait(get_nc_backup_provider(env, obj));
}


/*******************************************************************************
 * NcProvider
 ******************************************************************************/


static nc_provider_t* get_nc_provider(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "providerCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_provider_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcProvider_unrefProviderCPtr(JNIEnv *env, jobject obj)
{
    nc_provider_unref(get_nc_provider(env, obj));
}


JNIEXPORT jint Java_com_b44t_messenger_NcProvider_getStatus(JNIEnv *env, jobject obj)
{
    return (jint)nc_provider_get_status(get_nc_provider(env, obj));
}


JNIEXPORT jstring Java_com_b44t_messenger_NcProvider_getBeforeLoginHint(JNIEnv *env, jobject obj)
{
    char* temp = nc_provider_get_before_login_hint(get_nc_provider(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


JNIEXPORT jstring Java_com_b44t_messenger_NcProvider_getOverviewPage(JNIEnv *env, jobject obj)
{
    char* temp = nc_provider_get_overview_page(get_nc_provider(env, obj));
        jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}


/*******************************************************************************
 * NcJsonrpcInstance
 ******************************************************************************/

static nc_jsonrpc_instance_t* get_nc_jsonrpc_instance(JNIEnv *env, jobject obj)
{
    static jfieldID fid = 0;
    if (fid==0) {
        jclass cls = (*env)->GetObjectClass(env, obj);
        fid = (*env)->GetFieldID(env, cls, "jsonrpcInstanceCPtr", "J" /*Signature, J=long*/);
    }
    if (fid) {
        return (nc_jsonrpc_instance_t*)(*env)->GetLongField(env, obj, fid);
    }
    return NULL;
}


JNIEXPORT void Java_com_b44t_messenger_NcJsonrpcInstance_unrefJsonrpcInstanceCPtr(JNIEnv *env, jobject obj)
{
    nc_jsonrpc_unref(get_nc_jsonrpc_instance(env, obj));
}

JNIEXPORT void Java_com_b44t_messenger_NcJsonrpcInstance_request(JNIEnv *env, jobject obj, jstring request)
{
    CHAR_REF(request);
    nc_jsonrpc_request(get_nc_jsonrpc_instance(env, obj), requestPtr);
    CHAR_UNREF(request);
}

JNIEXPORT jstring Java_com_b44t_messenger_NcJsonrpcInstance_getNextResponse(JNIEnv *env, jobject obj)
{
    char* temp = nc_jsonrpc_next_response(get_nc_jsonrpc_instance(env, obj));
    jstring ret = JSTRING_NEW(temp);
    nc_str_unref(temp);
    return ret;
}
