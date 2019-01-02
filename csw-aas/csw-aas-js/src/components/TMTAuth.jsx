import { AASConfig, Config } from '../config/configs'
import KeyCloak from 'keycloak-js'
import { resolveAAS } from './AASResolver'

class TMTAuthStore {
  constructor() {
    this.token = null
    this.tokenParsed = null
    this.realmAccess = null
    this.resourceAccess = null
    this.isAuthenticated = false
  }

  from = keycloak => {
    this.logout = keycloak.logout
    this.token = keycloak.token
    this.tokenParsed = keycloak.tokenParsed
    this.realmAccess = keycloak.realmAccess
    this.resourceAccess = keycloak.resourceAccess
    this.loadUserInfo = keycloak.loadUserInfo
    this.isAuthenticated = keycloak.authenticated
    this.hasRealmRole = keycloak.hasRealmRole
    this.hasResourceRole = keycloak.hasResourceRole
    return this
  }

  authenticate = (config, url) => {
    console.info('instantiating AAS')
    const keycloakConfig = { ...AASConfig, ...config, ...url }
    const keycloak = KeyCloak(keycloakConfig)
    keycloak.onTokenExpired = () => {
      keycloak
        .updateToken(0)
        .success(function() {
          console.info('token refreshed successfully')
        })
        .error(function() {
          console.error(
            'Failed to refresh the token, or the session has expired',
          )
        })
    }
    const authenticated = keycloak.init({
      onLoad: 'login-required',
      flow: 'hybrid',
    })
    return { keycloak, authenticated }
  }

  getAASUrl = async () => {
    let url = await resolveAAS()
    return url || Config['AAS-server-url']
  }
}

export const TMTAuth = new TMTAuthStore()
