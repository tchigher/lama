package co.ledger.lama.bitcoin.service.http.model

import java.util.Date

/**
  * @param uid  for example: ''2a1e896d27628df31224786b62265fd55909ca90f8de45aed4a580102b5b4a29''
  * @param confirmations  for example: ''6''
  * @param blockHeight  for example: ''634340''
  * @param blockHash  for example: ''0000000000000000000c7f218573c47d5c16f75fcc7359210a2af7fa5dcf7a0c''
  * @param time  for example: ''2018-03-20T09:12:28Z''
  * @param `type`
  * @param senders  for example: ''["bc1q39p39f3ek09fes3nhmkss8lwxreqljl22qt42l"]''
  * @param recipients  for example: ''["13ZLzktrPVDGjaoPpqvWrxhXko7UAXFJHQ","bc1q4h6ukj0tcna7defc973p00d0l8a93l57s4wv65"]''
  * @param selfRecipients  for example: ''["13ZLzktrPVDGjaoPpqvWrxhXko7UAXFJHQ"]''
  * @param status
  * @param error
  */
case class Operation(
    uid: String,
    confirmations: Int,
    blockHeight: Int,
    blockHash: String,
    time: Date,
    `type`: String,
    senders: List[String],
    recipients: List[String],
    selfRecipients: List[String],
    status: String,
    error: Option[Error]
)
