package com.bland.launcher

/** Secular quotes on attention, time and doing the work. One per day, in order. */
object Quotes {
    val all = listOf(
        "How we spend our days is, of course, how we spend our lives." to "Annie Dillard",
        "It is not that we have a short time to live, but that we waste a lot of it." to "Seneca",
        "My experience is what I agree to attend to." to "William James",
        "Almost everything will work again if you unplug it for a few minutes, including you." to "Anne Lamott",
        "Your mind is for having ideas, not holding them." to "David Allen",
        "You do not rise to the level of your goals. You fall to the level of your systems." to "James Clear",
        "Clarity about what matters provides clarity about what does not." to "Cal Newport",
        "If you don't prioritize your life, someone else will." to "Greg McKeown",
        "We suffer more often in imagination than in reality." to "Seneca",
        "The impediment to action advances action. What stands in the way becomes the way." to "Marcus Aurelius",
        "Waste no more time arguing about what a good man should be. Be one." to "Marcus Aurelius",
        "First say to yourself what you would be; and then do what you have to do." to "Epictetus",
        "No man is free who is not master of himself." to "Epictetus",
        "We are what we repeatedly do. Excellence, then, is not an act, but a habit." to "Will Durant",
        "Every action you take is a vote for the type of person you wish to become." to "James Clear",
        "Lost time is never found again." to "Benjamin Franklin",
        "Well done is better than well said." to "Benjamin Franklin",
        "You can't use up creativity. The more you use, the more you have." to "Maya Angelou",
        "Nothing is particularly hard if you divide it into small jobs." to "Henry Ford",
        "The best way out is always through." to "Robert Frost",
        "Not all those who wander are lost." to "J.R.R. Tolkien",
        "Knowing is not enough; we must apply." to "Johann Wolfgang von Goethe",
        "Discipline equals freedom." to "Jocko Willink",
        "Be regular and orderly in your life, so that you may be violent and original in your work." to "Gustave Flaubert",
        "A year from now you may wish you had started today." to "Karen Lamb",
        "Life is long if you know how to use it." to "Seneca",
        "While we are postponing, life speeds by." to "Seneca",
        "Amateurs sit and wait for inspiration, the rest of us just get up and go to work." to "Stephen King",
        "You miss 100% of the shots you don't take." to "Wayne Gretzky",
        "Fall seven times, stand up eight." to "Japanese proverb",
        "Do the hard jobs first. The easy jobs will take care of themselves." to "Dale Carnegie",
        "Focus is a matter of deciding what things you're not going to do." to "John Carmack",
        "Focus means saying no to the hundred other good ideas." to "Steve Jobs",
        "Your time is limited, so don't waste it living someone else's life." to "Steve Jobs",
        "The first principle is that you must not fool yourself, and you are the easiest person to fool." to "Richard Feynman",
        "Concentrate all your thoughts upon the work at hand. The sun's rays do not burn until brought to a focus." to "Alexander Graham Bell",
        "What information consumes is rather obvious: it consumes the attention of its recipients." to "Herbert Simon",
        "The cost of a thing is the amount of life which is required to be exchanged for it." to "Henry David Thoreau",
        "Rest is not idleness." to "John Lubbock",
        "Done is better than perfect." to "Sheryl Sandberg",
        "The obstacle is the way." to "Ryan Holiday",
        "The best time to plant a tree was twenty years ago. The second best time is now." to "Proverb",
        "Suffer now and live the rest of your life as a champion." to "Muhammad Ali",
        "Nobody ever figures out what life is all about, and it doesn't matter. Explore the world." to "Richard Feynman",
        "The happiness of your life depends upon the quality of your thoughts." to "Marcus Aurelius",
    )

    fun today(): Pair<String, String> {
        val day = (System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())) / 86_400_000L
        return all[(day % all.size).toInt()]
    }
}
