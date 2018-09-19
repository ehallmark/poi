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
);

create index reddit_comment_comments_parent_id_idx on comment_comments (parent_id);
create index reddit_comment_comments_subreddit_id_idx on comment_comments (subreddit_id);


\copy (select * from comment_comments where score < 1 or controversiality > 0 order by random() limit 10000000) to /home/ehallmark/Downloads/comment_comments0.csv delimiter ',' csv header;
\copy (select * from comment_comments where score > 50 and controversiality = 0 order by random() limit 10000000) to /home/ehallmark/Downloads/comment_comments1.csv delimiter ',' csv header;
