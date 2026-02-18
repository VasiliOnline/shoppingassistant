package com.example.shoppingassistant.domain.profile

import com.example.shoppingassistant.domain.model.AuthUser

class UpdateProfilePhotosTask(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(photos: List<String>): AuthUser =
        repository.updatePhotos(photos)
}
