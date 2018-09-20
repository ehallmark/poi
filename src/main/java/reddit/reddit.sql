\connect reddit

drop table comments;

create table comments (
    id varchar(50) primary key,
    parent_id varchar(50),
    subreddit_id varchar(50),
    link_id varchar(50),
    text text not null,
    score int not null default(0),
    ups int not null default(0),
    author text not null,
    controversiality int not null default (0)
);

create index reddit_comments_parent_id_idx on comments (parent_id);
create index reddit_comments_subreddit_id_idx on comments (subreddit_id);


drop table comment_comments;
create table comment_comments (
    id varchar(50) primary key,
    parent_id varchar(50),
    subreddit_id varchar(50),
    link_id varchar(50),
    text text not null,
    score int not null default(0),
    ups int not null default(0),
    author text not null,
    controversiality int not null default (0),
    parent_link_id varchar(50),
    parent_text text not null,
    parent_score int not null default(0),
    parent_ups int not null default(0),
    parent_author text not null,
    parent_controversiality int not null default (0)
);

insert into comment_comments (
    select c.id,c.parent_id,c.subreddit_id,c.link_id,c.text,c.score,c.ups,c.author,c.controversiality,
        p.link_id,p.text,p.score,p.ups,p.author,p.controversiality
    from comments as c join comments as p on (c.parent_id='t1_'||p.id)
    where c.score > 50 or c.score < -5
);

create index reddit_comment_comments_parent_id_idx on comment_comments (parent_id);
create index reddit_comment_comments_subreddit_id_idx on comment_comments (subreddit_id);


\copy (select * from comment_comments order by controversiality desc, score asc, random() limit 1100000) to /home/ehallmark/Downloads/comment_comments0.csv delimiter ',' csv header;
\copy (select * from comment_comments where controversiality = 0 order by score desc, random() limit 1100000) to /home/ehallmark/Downloads/comment_comments1.csv delimiter ',' csv header;


create table comments_words (
    word text primary key,
    docs_appeared_in double precision not null,
    pos_docs_appeared_in double precision not null,
    neg_docs_appeared_in double precision not null
);

insert into comments_words (
    select word, count(*),
    sum(case when controversiality = 1 or score < 0 then 1 else 0 end),
    sum(case when controversiality != 1 and score > 0 then 1 else 0 end)
    from comment_comments, unnest(string_to_array(regexp_replace(lower(text), '[^a-z ]', ' ', 'g'),' ')) as w(word)
    group by word
    having count(*) > 100
);

