import Principal "mo:base/Principal";
import Time "mo:base/Time";

actor class LoanTest() = this{
     // Loan Application
  public type LoanApplication = {
    id: Nat;
    firstname: Text;
    lastname: Text;
    zipcode: Text;
    ssn: Text;
    amount: Float;
    term: Nat16;
    created: Int;
  };

 // Credit Check Request
  public type CreditRequest = {
    userid: Principal;   
    firstname: Text;
    lastname: Text;
    zipcode: Text;
    ssn: Text;
    created: Int;   
  };

   // Credit Check
  public type Credit = {
    userid: Principal;   
    rating: Nat16;
    created: Int;
  };

   // Loan Offer Request
  public type LoanOfferRequest = {
    userid: Principal;   
    applicationid: Nat;
    amount: Float;
    term: Nat16;   
    rating: Nat16;
    zipcode: Text;
    created: Int;    
  };

   // Loan Offer
    public type LoanOffer = {
        providerid: Principal;
        providername: Text;
        userid: Principal;   
        applicationid: Nat;
        apr: Float;
        created: Int;
    }; 

    public shared query func getName() : async ?Text
    {
        return ?"Name";
    };

    public shared query func echoApplications(applications : [LoanApplication]) : async [LoanApplication] {
        return applications;
    }; 

    public shared query func echoCreditRequests(requests : [CreditRequest]) : async [CreditRequest] {
        return requests;
    }; 

    public shared query func echoCredits(credits : [Credit]) : async [Credit] {
        return credits;
    };     

    public shared query func echoOfferRequests(requests : [LoanOfferRequest]) : async [LoanOfferRequest] {
        return requests;
    }; 

    public shared query func echoOffers(offers : [LoanOffer]) : async [LoanOffer] {
        return offers;
    }; 

     public shared query (msg) func getOffer() : async LoanOffer {
        let offer  : LoanOffer = {
            providerid = Principal.fromActor(this);
            providername = "Loan Provider";
            userid = msg.caller;
            applicationid = 1;
            apr = 3.14;
            created = Time.now();
        };
        return offer;
    };        
} 