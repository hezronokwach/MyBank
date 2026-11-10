package com.example.mybank.enums;

// How an attempt to see a card's full details ended
public enum CardRevealOutcome {
    SHOWN,      // the right PIN: the details were shown
    WRONG_PIN,  // nothing was shown
    LOCKED      // too many wrong PINs: nothing was shown
}
