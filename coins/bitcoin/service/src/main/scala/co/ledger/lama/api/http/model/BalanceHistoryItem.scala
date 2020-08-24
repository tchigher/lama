package co.ledger.lama.api.http.model

import java.util.Date

/**
 * @param timestamp  for example: ''2018-03-20T09:12:28Z''
 * @param balance  for example: ''10000''
 */
case class BalanceHistoryItem (
  timestamp: Date,
  balance: String
)

