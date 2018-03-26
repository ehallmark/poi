\connect reddit

drop index reddit_comments_parent_id_idx;
drop index reddit_comments_subreddit_id_idx;
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

