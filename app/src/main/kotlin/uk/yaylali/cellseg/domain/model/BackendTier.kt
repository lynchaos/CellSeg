package uk.yaylali.cellseg.domain.model

enum class BackendTier {
    LOCAL_CYTO3,
    REMOTE_PUBLIC_HF,
    REMOTE_PRIVATE_HF,
    REMOTE_INFERENCE_ENDPOINT;   // placeholder, not wired in v1

    val isRemote: Boolean get() = this != LOCAL_CYTO3

    val displayName: String get() = when (this) {
        LOCAL_CYTO3 -> "On-device (Cyto3)"
        REMOTE_PUBLIC_HF -> "Cloud (HF Spaces)"
        REMOTE_PRIVATE_HF -> "Private HF Space"
        REMOTE_INFERENCE_ENDPOINT -> "Inference Endpoint"
    }

    companion object {
        val HF_PUBLIC_SPACE_SLUG = "mouseland-cellpose"
    }
}
