persistent actor {
    var value : Text = "";
    public func greet(next : Text) : async Text {
        value := next;
        value
    };
    public query func getName() : async Text {
        value
    };
}
