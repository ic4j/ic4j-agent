package org.ic4j.agent.test;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TestProperties extends Properties{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	protected static final Logger LOG = LoggerFactory.getLogger(TestProperties.class);

	
	
	static String PROPERTIES_FILE_NAME = "test.properties";
	
	static String MOCK_PORT_PROPERTY = "mockPort";
	static String CANISTER_ID_PROPERTY = "canisterId";
	static String FORWARD_PROPERTY = "forward";
	static String FORWARD_HOST_PROPERTY = "forwardHost";
	static String FORWARD_PORT_PROPERTY = "forwardPort";
	static String STORE_PROPERTY = "store";
	static String STORE_PATH_PROPERTY = "storePath";
	static String IC_URL_PROPERTY = "icUrl";
	static String IC_CANISTER_ID_PROPERTY = "icCanisterId";
	static String LOAN_URL_PROPERTY = "loanUrl";
	static String LOAN_CANISTER_ID_PROPERTY = "loanCanisterId";	
	static String TRANSPORT_TYPE_ID_PROPERTY = "transportType";

	protected static Integer MOCK_PORT = 8777;
	
	protected static Boolean FORWARD = false;
	
	protected static String FORWARD_HOST = "localhost";
	protected static Integer FORWARD_PORT = 8000;
	
	protected static Boolean STORE = false;
	protected static String STORE_PATH = "/tmp";	
	
	protected static String CANISTER_ID = "rrkah-fqaaa-aaaaa-aaaaq-cai";
	
	protected static String IC_URL;
	protected static String IC_CANISTER_ID;	
	
	protected static String LOAN_URL;
	protected static String LOAN_CANISTER_ID;		
	
	protected static String CBOR_STATUS_RESPONSE_FILE = "cbor.status.response";
	
	protected static String CBOR_ECHOBOOL_QUERY_RESPONSE_FILE = "cbor.echoBool.query.response";
	protected static String CBOR_ECHOINT_QUERY_RESPONSE_FILE = "cbor.echoInt.query.response";
	protected static String CBOR_ECHOFLOAT_QUERY_RESPONSE_FILE = "cbor.echoFloat.query.response";
	protected static String CBOR_ECHOOPT_QUERY_RESPONSE_FILE = "cbor.echoOption.query.response";
	protected static String CBOR_ECHOVEC_QUERY_RESPONSE_FILE = "cbor.echoVec.query.response";
	protected static String CBOR_ECHOBINARY_QUERY_RESPONSE_FILE = "cbor.echoBinary.query.response";
	protected static String CBOR_ECHORECORD_QUERY_RESPONSE_FILE = "cbor.echoRecord.query.response";
	protected static String CBOR_ECHOPRINCIPAL_QUERY_RESPONSE_FILE = "cbor.echoPrincipal.query.response";	

	protected static String CBOR_PEEK_QUERY_RESPONSE_FILE = "cbor.peek.query.response";
	protected static String CBOR_HELLO_QUERY_RESPONSE_FILE = "cbor.hello.query.response";	
	
	protected static String CBOR_UPDATE_GREET_RESPONSE_FILE = "cbor.update.greet.response";
	
	protected static String BINARY_IMAGE_FILE = "dfinity.png";
	
	
	protected static String ED25519_IDENTITY_FILE = "Ed25519_identity.pem";	
	protected static String SECP256K1_IDENTITY_FILE = "Secp256k1_identity.pem";	
	protected static String PRIME256V1_IDENTITY_FILE = "Prime256v1_identity.pem";	
	
	protected static String SECP256K1_IDENTITY_PRIVATE_FILE = "Secp256k1_identity_private.pem";	
	protected static String PRIME256V1_IDENTITY_PRIVATE_FILE = "Prime256v1_identity_private.pem";	
	
	protected static  String TRANSPORT_TYPE = "http.apache";
	
	static
	{	 
		InputStream propInputStream = TestProperties.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME);

		try {
			Properties props = new Properties();
			props.load(propInputStream);
			
			MOCK_PORT = Integer.valueOf(props.getProperty(MOCK_PORT_PROPERTY, "8777"));
			FORWARD = Boolean.valueOf(props.getProperty(FORWARD_PROPERTY, "false"));
			FORWARD_HOST = props.getProperty(FORWARD_HOST_PROPERTY, "localhost");
			FORWARD_PORT = Integer.valueOf(props.getProperty(FORWARD_PORT_PROPERTY, "8000"));
			STORE = Boolean.valueOf(props.getProperty(STORE_PROPERTY, "false"));
			IC_URL = props.getProperty(IC_URL_PROPERTY);
			IC_CANISTER_ID = props.getProperty(IC_CANISTER_ID_PROPERTY);
			LOAN_URL = props.getProperty(LOAN_URL_PROPERTY);
			LOAN_CANISTER_ID = props.getProperty(LOAN_CANISTER_ID_PROPERTY);			
			
		    TRANSPORT_TYPE = props.getProperty(TRANSPORT_TYPE_ID_PROPERTY,"http.apache");
		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
		}
	}
}
