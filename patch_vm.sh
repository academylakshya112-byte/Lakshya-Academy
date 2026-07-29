sed -i '/init {/a \
        viewModelScope.launch {\
            repository.syncAllFromRemote()\
        }' app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt
