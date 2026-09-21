package com.sinelynx.grindingrobot.core.data.repository

import android.net.Uri
import com.sinelynx.grindingrobot.core.model.response.NetworkResponse
import com.sinelynx.grindingrobot.core.network.datasource.fileupload.FileUploadNetworkDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 文件上传仓库
 * 提供文件上传的业务接口，实际上传逻辑委托给 network 层处理
 *
 * @param fileUploadNetworkDataSource 文件上传网络数据源
 * @author Dreamj
 */
class FileUploadRepository @Inject constructor(
    private val fileUploadNetworkDataSource: FileUploadNetworkDataSource
) {

    /**
     * 上传图片文件
     *
     * @param imageUri 图片URI
     * @return 上传结果Flow，包含文件URL
     * @author Dreamj
     */
    fun uploadImage(imageUri: Uri): Flow<NetworkResponse<String>> {
        return fileUploadNetworkDataSource.uploadImage(imageUri)
    }

    /**
     * 批量上传图片
     *
     * @param imageUris 图片URI列表
     * @return 上传结果Flow，包含文件URL列表
     * @author Dreamj
     */
    fun uploadImages(imageUris: List<Uri>): Flow<NetworkResponse<List<String>>> {
        return fileUploadNetworkDataSource.uploadImages(imageUris)
    }
}
