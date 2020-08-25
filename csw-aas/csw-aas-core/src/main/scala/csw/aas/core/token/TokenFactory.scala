package csw.aas.core.token

import csw.aas.core.{TokenVerificationFailure, TokenVerifier}

import scala.concurrent.{ExecutionContext, Future}

class TokenFactory(tokenVerifier: TokenVerifier)(implicit ec: ExecutionContext) {

  /**
   * It will validate the token string for signature and expiry and then decode it into
   * [[csw.aas.core.token.AccessToken]]
   *
   * @param token Access token string
   */
  private[aas] def makeToken(token: String): Future[Either[TokenVerificationFailure, AccessToken]] =
    tokenVerifier.verifyAndDecode(token)
}
