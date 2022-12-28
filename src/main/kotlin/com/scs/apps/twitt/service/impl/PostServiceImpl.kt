package com.scs.apps.twitt.service.impl

import com.google.protobuf.Timestamp
import com.scs.apps.twitt.PostCdcKey
import com.scs.apps.twitt.PostCdcMessage
import com.scs.apps.twitt.constant.KafkaTopic
import com.scs.apps.twitt.dto.RequestDto
import com.scs.apps.twitt.entity.Author
import com.scs.apps.twitt.entity.Post
import com.scs.apps.twitt.exception.NotFoundException
import com.scs.apps.twitt.producer.StreamsProducer
import com.scs.apps.twitt.repository.AuthorJPARepository
import com.scs.apps.twitt.repository.PostJPARepository
import com.scs.apps.twitt.serde.PostCdcSerde
import com.scs.apps.twitt.service.PostService
import com.scs.apps.twitt.utils.DateTimeUtils
import org.apache.kafka.streams.KeyValue
import org.springframework.stereotype.Service
import java.util.*


@Service
class PostServiceImpl(
    private val streamsProducer: StreamsProducer, private val postCdcSerde: PostCdcSerde,
    private val authorRepository: AuthorJPARepository, private val postRepository: PostJPARepository,
    private val dateTimeUtils: DateTimeUtils
) : PostService {

    override fun createPost(createPostRequestDto: RequestDto.CreatePostRequestDto, userId: String) {
        val author: Author = authorRepository.findById(UUID.fromString(userId))
            .orElseThrow { NotFoundException("User with id $userId is not found.") }

        val post = Post(
            content = createPostRequestDto.content, title = createPostRequestDto.title,
            creator = author
        )

        val savedPost: Post = postRepository.save(post)
        publishChangedPost(savedPost)
    }

    private fun publishChangedPost(post: Post) {
        val messageKey: PostCdcKey = PostCdcKey.newBuilder()
            .setId(post.id.toString())
            .build()

        val createdAt: Timestamp? = post.createdAt?.let { dateTimeUtils.parseToTimestamp(it) }
        val message: PostCdcMessage = PostCdcMessage.newBuilder()
            .setId(post.id.toString())
            .setCreatorId(post.creator?.id.toString())
            .setCreatedAt(createdAt)
            .setUpdatedAt(createdAt)
            .setContent(post.content)
            .setTitle(post.title)
            .setIsDeleted(false)
            .build()

        streamsProducer.publish(
            KafkaTopic.POST_CREATED_TOPIC, KeyValue.pair(messageKey, message), postCdcSerde.postCdcKeySerde(),
            postCdcSerde.postCdcMessageSerde()
        )
    }
}